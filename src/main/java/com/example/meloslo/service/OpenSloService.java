package com.example.meloslo.service;

import com.example.meloslo.dto.DashboardStats;
import com.example.meloslo.dto.BusinessServiceReport;
import com.example.meloslo.dto.SliReport;
import com.example.meloslo.dto.SloReport;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.service.DepartmentAccessService.AccessScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class OpenSloService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenSloService.class);

    private final OpenSloRepository repository;
    private final MetricRepository metricRepository;
    private final DepartmentAccessService accessService;
    private final OpenSloRedactor redactor;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Autowired
    public OpenSloService(OpenSloRepository repository, MetricRepository metricRepository,
                          DepartmentAccessService accessService, OpenSloRedactor redactor) {
        this.repository = repository;
        this.metricRepository = metricRepository;
        this.accessService = accessService;
        this.redactor = redactor;
    }

    private List<OpenSlo> filterAccessible(List<OpenSlo> records, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return records;
        }
        // Initialize collections within the transaction to avoid LazyInitializationException
        for (OpenSlo record : records) {
            if (record.getSlis() != null) record.getSlis().size();
            if (record.getSlos() != null) record.getSlos().size();
            if (record.getIndicatorSlis() != null) record.getIndicatorSlis().size();
            if (record.getLinkedSlos() != null) record.getLinkedSlos().size();
        }
        return records.stream().filter(scope::canAccess).collect(Collectors.toList());
    }

    private OpenSlo findAccessibleOrThrow(Long id, AccessScope scope) {
        return repository.findById(id)
                .filter(scope::canAccess)
                .orElseThrow(() -> new RuntimeException("OpenSLO record not found with id " + id));
    }

    private List<OpenSlo> visible(List<OpenSlo> records, AccessScope scope) {
        if (records == null) {
            return new ArrayList<>();
        }
        if (scope.isUnrestricted()) {
            return records;
        }
        return records.stream().filter(scope::canAccess).collect(Collectors.toList());
    }

    /** Child SLOs of the given service that the current caller may see. */
    public List<OpenSlo> getAccessibleSlos(OpenSlo service) {
        return visible(service.getSlos(), accessService.currentScope());
    }

    @Transactional(readOnly = true)
    public List<OpenSlo> getAllRecords() {
        AccessScope scope = accessService.currentScope();
        List<OpenSlo> records = filterAccessible(repository.findAll(), scope);
        populateTransientFieldsForList(records, scope);
        return redactor.redactAll(records, scope);
    }

    @Transactional(readOnly = true)
    public List<OpenSlo> getRecordsByKind(String kind) {
        AccessScope scope = accessService.currentScope();
        List<OpenSlo> records = filterAccessible(repository.findByKind(kind), scope);
        populateTransientFieldsForList(records, scope);
        return redactor.redactAll(records, scope);
    }

    @Transactional(readOnly = true)
    public Optional<OpenSlo> getRecordById(Long id) {
        AccessScope scope = accessService.currentScope();
        return repository.findById(id)
                .filter(scope::canAccess)
                .map(r -> {
                    populateTransientFields(r, scope);
                    return redactor.redact(r, scope);
                });
    }

    @Transactional(readOnly = true)
    public void populateTransientFieldsForList(List<OpenSlo> records) {
        populateTransientFieldsForList(records, accessService.currentScope());
    }

    private void populateTransientFieldsForList(List<OpenSlo> records, AccessScope scope) {
        List<OpenSlo> slos = records.stream()
                .filter(r -> "SLO".equalsIgnoreCase(r.getKind()))
                .collect(Collectors.toList());

        if (!slos.isEmpty()) {
            // Find all unique SLIs across all SLOs
            List<OpenSlo> allSlis = slos.stream()
                    .flatMap(slo -> visible(slo.getSlis(), scope).stream())
                    .distinct()
                    .collect(Collectors.toList());

            if (!allSlis.isEmpty()) {
                // Determine the maximum window size required
                int maxWindowDays = slos.stream()
                        .mapToInt(slo -> parseWindowDaysFromSpec(slo.getSpec()))
                        .max()
                        .orElse(30);
                LocalDateTime windowStart = LocalDateTime.now().minusDays(maxWindowDays);

                // Batch fetch all relevant metrics for all SLIs
                List<SliMetric> allMetrics = metricRepository.findBySliInAndTimestampAfterOrderByTimestampDesc(allSlis, windowStart);
                Map<Long, List<SliMetric>> metricsBySliId = allMetrics.stream()
                        .collect(Collectors.groupingBy(m -> m.getSli().getId()));

                // Now populate each SLO using the pre-fetched metrics
                for (OpenSlo slo : slos) {
                    double target = parseTargetFromSpec(slo.getSpec());
                    int windowDays = parseWindowDaysFromSpec(slo.getSpec());
                    LocalDateTime sloWindowStart = LocalDateTime.now().minusDays(windowDays);

                    List<SliMetric> sloMetrics = visible(slo.getSlis(), scope).stream()
                            .flatMap(sli -> metricsBySliId.getOrDefault(sli.getId(), new ArrayList<>()).stream())
                            .filter(m -> m.getTimestamp().isAfter(sloWindowStart))
                            .collect(Collectors.toList());

                    double currentSloValue = sloMetrics.stream()
                            .mapToDouble(SliMetric::getValue)
                            .average()
                            .orElse(1.0);

                    double errorBudget = (1.0 - target > 0) ? (currentSloValue - target) / (1.0 - target) * 100 : 100.0;
                    slo.setErrorBudget(errorBudget);
                    slo.setStatus(determineStatus(errorBudget));
                    slo.setCurrentValue(currentSloValue);
                }
            }
        }

        // Handle BusinessService if needed - though they might depend on populated SLOs
        for (OpenSlo record : records) {
            if ("BusinessService".equalsIgnoreCase(record.getKind())) {
                populateTransientFields(record, scope);
            }
        }
    }

    public void populateTransientFields(OpenSlo record) {
        populateTransientFields(record, accessService.currentScope());
    }

    private void populateTransientFields(OpenSlo record, AccessScope scope) {
        if ("SLO".equalsIgnoreCase(record.getKind())) {
            SloReport report = calculateSloReport(record, scope);
            record.setErrorBudget(report.getErrorBudget());
            record.setStatus(determineStatus(report.getErrorBudget()));
            record.setCurrentValue(report.getCurrentValue());
        } else if ("BusinessService".equalsIgnoreCase(record.getKind())) {
            List<OpenSlo> childSlos = visible(record.getSlos(), scope);
            if (!childSlos.isEmpty()) {
                double totalValue = 0;
                double minErrorBudget = Double.MAX_VALUE;
                for (OpenSlo slo : childSlos) {
                    SloReport report = calculateSloReport(slo, scope);
                    totalValue += report.getCurrentValue();
                    if (report.getErrorBudget() < minErrorBudget) {
                        minErrorBudget = report.getErrorBudget();
                    }
                }
                record.setCurrentValue(totalValue / childSlos.size());
                record.setErrorBudget(minErrorBudget);
                record.setStatus(determineStatus(minErrorBudget));
            } else {
                record.setCurrentValue(1.0);
                record.setErrorBudget(100.0);
                record.setStatus("Healthy");
            }
        }
    }

    @Transactional(readOnly = true)
    public Optional<OpenSlo> getRecordByName(String name) {
        AccessScope scope = accessService.currentScope();
        return repository.findByName(name)
                .filter(scope::canAccess)
                .map(r -> redactor.redact(r, scope));
    }

    @Transactional
    public OpenSlo createRecord(OpenSlo record) {
        if (record.getId() != null) {
            throw new IllegalArgumentException("A new record must not have an id");
        }
        AccessScope scope = accessService.currentScope();
        if (!scope.canAssign(record.getDepartment())) {
            throw new AccessDeniedException("You can only create records in your own departments");
        }
        return redactor.redact(repository.save(record), scope);
    }

    @Transactional
    public OpenSlo updateRecord(Long id, OpenSlo updatedRecord) {
        AccessScope scope = accessService.currentScope();
        OpenSlo record = findAccessibleOrThrow(id, scope);
        if (!scope.canAssign(updatedRecord.getDepartment())) {
            throw new AccessDeniedException("You can only assign records to your own departments");
        }
        record.setApiVersion(updatedRecord.getApiVersion());
        record.setKind(updatedRecord.getKind());
        record.setName(updatedRecord.getName());
        record.setDisplayName(updatedRecord.getDisplayName());
        record.setSpec(updatedRecord.getSpec());
        record.setDepartment(updatedRecord.getDepartment());
        record.setManager(updatedRecord.getManager());
        record.setRefreshRate(updatedRecord.getRefreshRate());
        record.setLastRefreshTime(updatedRecord.getLastRefreshTime());
        record.setAlertUrl(updatedRecord.getAlertUrl());
        record.setAlertPayload(updatedRecord.getAlertPayload());
        record.setAlertingSource(updatedRecord.getAlertingSource());
        record.setService(updatedRecord.getService());
        record.setDatasource(updatedRecord.getDatasource());
        record.setSlis(updatedRecord.getSlis());
        record.setSlos(updatedRecord.getSlos());
        return redactor.redact(repository.save(record), scope);
    }

    @Transactional
    public void deleteRecord(Long id) {
        AccessScope scope = accessService.currentScope();
        OpenSlo record = findAccessibleOrThrow(id, scope);

        // Deleting an AlertingSource unlinks the SLOs that use it. Check every dependent before
        // changing anything, so a restricted caller cannot modify another department's SLO.
        List<OpenSlo> dependents = new ArrayList<>();
        if ("AlertingSource".equalsIgnoreCase(record.getKind())) {
            for (OpenSlo slo : repository.findByKind("SLO")) {
                if (slo.getAlertingSource() != null && slo.getAlertingSource().getId().equals(record.getId())) {
                    dependents.add(slo);
                }
            }
            if (dependents.stream().anyMatch(d -> !scope.canAccess(d))) {
                throw new AccessDeniedException("This alerting source is used by SLOs in other departments");
            }
        }

        metricRepository.deleteBySli(record);
        metricRepository.deleteByDatasource(record);
        for (OpenSlo slo : dependents) {
            slo.setAlertingSource(null);
            repository.save(slo);
        }
        repository.delete(record);
    }

    public SliMetric createMetric(SliMetric metric) {
        return metricRepository.save(metric);
    }

    public List<SliMetric> getAllMetrics() {
        return metricRepository.findAll();
    }

    public List<SliMetric> getMetricsBySli(OpenSlo sli) {
        return metricRepository.findBySliOrderByTimestampDesc(sli);
    }

    @Transactional(readOnly = true)
    public SliReport getSliReport(Long id) {
        AccessScope scope = accessService.currentScope();
        OpenSlo sli = findAccessibleOrThrow(id, scope);

        if (!"SLI".equalsIgnoreCase(sli.getKind())) {
            throw new RuntimeException("Record is not of kind SLI");
        }

        List<SliMetric> allMetrics = metricRepository.findBySliOrderByTimestampDesc(sli);
        
        double currentValue = allMetrics.stream()
                .mapToDouble(SliMetric::getValue)
                .average()
                .orElse(0.0);

        // Calculate average target from associated SLOs
        List<OpenSlo> allSlos = repository.findByKind("SLO");
        double totalTarget = 0;
        int targetCount = 0;
        for (OpenSlo slo : visible(allSlos, scope)) {
            if (slo.getSlis() != null && slo.getSlis().contains(sli)) {
                totalTarget += parseTargetFromSpec(slo.getSpec());
                targetCount++;
            }
        }
        Double avgTarget = targetCount > 0 ? (totalTarget / targetCount) : null;

        // Take up to last 100 metrics for display in SLI view
        List<SliMetric> recentMetrics = allMetrics.size() > 100 ? allMetrics.subList(0, 100) : allMetrics;

        return new SliReport(sli.getName(), sli.getDisplayName(), currentValue, avgTarget,
                redactor.redactMetrics(recentMetrics, scope));
    }

    @Transactional(readOnly = true)
    public SloReport getSloReport(Long id) {
        AccessScope scope = accessService.currentScope();
        OpenSlo slo = findAccessibleOrThrow(id, scope);

        if (!"SLO".equalsIgnoreCase(slo.getKind())) {
            throw new RuntimeException("Record is not of kind SLO");
        }

        return calculateSloReport(slo, scope);
    }

    @Transactional(readOnly = true)
    public DashboardStats getDashboardStats() {
        AccessScope scope = accessService.currentScope();
        List<OpenSlo> filteredServices = filterAccessible(repository.findByKind("BusinessService"), scope);
        List<OpenSlo> filteredSlos = filterAccessible(repository.findByKind("SLO"), scope);
        
        long healthy = 0;
        long warning = 0;
        long breaching = 0;
        
        for (OpenSlo slo : filteredSlos) {
            SloReport report = calculateSloReport(slo, scope);
            String status = determineStatus(report.getErrorBudget());
            if ("Healthy".equalsIgnoreCase(status)) {
                healthy++;
            } else if ("Warning".equalsIgnoreCase(status)) {
                warning++;
            } else {
                breaching++;
            }
        }
        
        return new DashboardStats(filteredServices.size(), filteredSlos.size(), healthy, warning, breaching);
    }

    private String determineStatus(double errorBudget) {
        if (errorBudget >= 25) {
            return "Healthy";
        } else if (errorBudget >= 0) {
            return "Warning";
        } else {
            return "Breaching";
        }
    }

    @Transactional(readOnly = true)
    public SloReport calculateSloReport(OpenSlo slo) {
        return calculateSloReport(slo, accessService.currentScope());
    }

    private SloReport calculateSloReport(OpenSlo slo, AccessScope scope) {
        double target = parseTargetFromSpec(slo.getSpec());
        int windowDays = parseWindowDaysFromSpec(slo.getSpec());
        LocalDateTime windowStart = LocalDateTime.now().minusDays(windowDays);

        List<SliMetric> allMetrics = new ArrayList<>();
        for (OpenSlo sli : visible(slo.getSlis(), scope)) {
            allMetrics.addAll(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(sli, windowStart));
        }

        double currentSloValue = allMetrics.stream()
                .mapToDouble(SliMetric::getValue)
                .average()
                .orElse(1.0);

        double errorBudget = (1.0 - target > 0) ? (currentSloValue - target) / (1.0 - target) * 100 : 100.0;
        String status = determineStatus(errorBudget);

        List<SliMetric> recentMetrics = allMetrics.size() > 50 ? allMetrics.subList(0, 50) : allMetrics;

        // Calculate trend using all available data up to 1 year
        List<Double> trendPoints = calculateTrendPoints(allMetrics, target, 30);

        // Calculate 30-day historical SLO values
        List<Double> historicalSloValues = calculateHistoricalSloValues(allMetrics, 30);

        return new SloReport(slo.getName(), target, currentSloValue, errorBudget, status,
                redactor.redactMetrics(recentMetrics, scope), trendPoints, historicalSloValues);
    }

    private List<Double> calculateHistoricalSloValues(List<SliMetric> metrics, int days) {
        if (metrics.isEmpty()) return new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(days);

        // Group by day and calculate average SLI per day
        Map<java.time.LocalDate, Double> dailySli = metrics.stream()
                .filter(m -> m.getTimestamp() != null && m.getTimestamp().isAfter(startDate))
                .collect(java.util.stream.Collectors.groupingBy(
                        m -> m.getTimestamp().toLocalDate(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.averagingDouble(SliMetric::getValue)
                ));

        List<Double> results = new ArrayList<>();
        // Fill in gaps and ensure we have exactly 'days' points if possible
        for (int i = days - 1; i >= 0; i--) {
            java.time.LocalDate date = now.minusDays(i).toLocalDate();
            results.add(dailySli.getOrDefault(date, 1.0) * 100.0);
        }

        return results;
    }

    public List<Double> calculateTrendPoints(List<SliMetric> metrics, double target, int futureDays) {
        if (metrics.isEmpty()) return new ArrayList<>();

        // 1. Group by day and calculate daily average for up to 1 year
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pastOneYear = now.minusYears(1);

        // Group by LocalDate and calculate average SLI per day
        Map<java.time.LocalDate, Double> dailySli = metrics.stream()
                .filter(m -> m.getTimestamp() != null && m.getTimestamp().isAfter(pastOneYear))
                .collect(java.util.stream.Collectors.groupingBy(
                        m -> m.getTimestamp().toLocalDate(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.averagingDouble(SliMetric::getValue)
                ));

        if (dailySli.size() < 2) return new ArrayList<>();

        // 2. Convert to Error Budget points and prepare for regression
        List<java.time.LocalDate> dates = new ArrayList<>(dailySli.keySet());
        List<Double> y = new ArrayList<>();
        for (java.time.LocalDate date : dates) {
            double sli = dailySli.get(date);
            double eb = (1.0 - target > 0) ? (sli - target) / (1.0 - target) * 100 : 100.0;
            y.add(eb);
        }

        // 3. Meta Prophet-inspired Additive Model: y(t) = g(t) + s(t)
        // g(t): Linear Trend
        int n = y.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += y.get(i);
            sumXY += i * y.get(i);
            sumXX += (double) i * i;
        }
        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        // s(t): Weekly Seasonality (averaging residuals by day of week)
        double[] seasonality = new double[7]; // 0=Mon, 6=Sun
        int[] counts = new int[7];
        for (int i = 0; i < n; i++) {
            double trendValue = intercept + slope * i;
            double residual = y.get(i) - trendValue;
            int dayOfWeek = dates.get(i).getDayOfWeek().getValue() - 1;
            seasonality[dayOfWeek] += residual;
            counts[dayOfWeek]++;
        }
        for (int i = 0; i < 7; i++) {
            if (counts[i] > 0) seasonality[i] /= counts[i];
        }

        // 4. Project future days: y_hat = g(t) + s(t)
        List<Double> projection = new ArrayList<>();
        java.time.LocalDate lastDate = dates.get(n - 1);
        for (int i = 1; i <= futureDays; i++) {
            java.time.LocalDate forecastDate = lastDate.plusDays(i);
            double trendValue = intercept + slope * (n - 1 + i);
            int dayOfWeek = forecastDate.getDayOfWeek().getValue() - 1;
            double seasonalValue = seasonality[dayOfWeek];
            
            projection.add(trendValue + seasonalValue);
        }

        return projection;
    }

    @Transactional(readOnly = true)
    public BusinessServiceReport getServiceReport(Long id) {
        AccessScope scope = accessService.currentScope();
        OpenSlo service = findAccessibleOrThrow(id, scope);

        if (!"BusinessService".equalsIgnoreCase(service.getKind())) {
            throw new RuntimeException("Record is not of kind BusinessService");
        }

        List<SloReport> sloReports = new ArrayList<>();
        for (OpenSlo slo : visible(service.getSlos(), scope)) {
            sloReports.add(calculateSloReport(slo, scope));
        }

        return new BusinessServiceReport(service.getName(), service.getDisplayName(), sloReports);
    }

    public Double parseTargetFromSpec(String spec) {
        try {
            JsonNode root = yamlMapper.readTree(spec);
            JsonNode objectives = root.path("spec").path("objectives");
            if (objectives.isArray() && objectives.size() > 0) {
                return objectives.get(0).path("target").asDouble(0.99);
            }
        } catch (Exception e) {
            // Default 0.99
        }
        return 0.99;
    }

    public int parseWindowDaysFromSpec(String spec) {
        try {
            JsonNode root = yamlMapper.readTree(spec);
            JsonNode timeWindow = root.path("spec").path("timeWindow");
            if (timeWindow.isArray() && timeWindow.size() > 0) {
                String duration = timeWindow.get(0).path("duration").asText("30d");
                if (duration.endsWith("d")) {
                    return Integer.parseInt(duration.replace("d", ""));
                }
            }
        } catch (Exception e) {
            // Default 30 days
        }
        return 30;
    }
}
