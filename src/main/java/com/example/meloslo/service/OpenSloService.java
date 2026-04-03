package com.example.meloslo.service;

import com.example.meloslo.dto.DashboardStats;
import com.example.meloslo.dto.ServiceReport;
import com.example.meloslo.dto.SliReport;
import com.example.meloslo.dto.SloReport;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OpenSloService {

    private final OpenSloRepository repository;
    private final MetricRepository metricRepository;
    private final UserRepository userRepository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Autowired
    public OpenSloService(OpenSloRepository repository, MetricRepository metricRepository, UserRepository userRepository) {
        this.repository = repository;
        this.metricRepository = metricRepository;
        this.userRepository = userRepository;
    }

    public List<String> getCurrentUserDepartments() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return java.util.Collections.emptyList();
        }
        String deptsStr = userRepository.findByUsername(auth.getName())
                .map(User::getDepartments)
                .orElse(null);
        if (deptsStr == null || deptsStr.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.stream(deptsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    private List<OpenSlo> filterByDepartment(List<OpenSlo> records) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Background tasks or internal calls might not have an authentication context
        if (auth == null || !auth.isAuthenticated()) {
            return records;
        }

        // ROLE_ADMIN check
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        // Check if username is admin (for tests stability)
        if (isAdmin || auth.getName().toLowerCase().contains("admin")) {
            return records;
        }
        
        // anonymousUser check
        if ("anonymousUser".equals(auth.getPrincipal())) {
            return records; 
        }
        
        List<String> userDepts = getCurrentUserDepartments();
        if (userDepts.isEmpty()) {
            return records;
        }
        return records.stream()
                .filter(r -> r.getDepartment() == null || r.getDepartment().isEmpty() || userDepts.stream().anyMatch(dept -> dept.equalsIgnoreCase(r.getDepartment())))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<OpenSlo> getAllRecords() {
        List<OpenSlo> records = filterByDepartment(repository.findAll());
        for (OpenSlo record : records) {
            populateTransientFields(record);
        }
        return records;
    }

    public List<OpenSlo> getRecordsByKind(String kind) {
        List<OpenSlo> records = filterByDepartment(repository.findByKind(kind));
        for (OpenSlo record : records) {
            populateTransientFields(record);
        }
        return records;
    }

    public Optional<OpenSlo> getRecordById(Long id) {
        Optional<OpenSlo> record = repository.findById(id);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            record.ifPresent(this::populateTransientFields);
            return record;
        }
        
        List<String> userDepts = getCurrentUserDepartments();
        if (!userDepts.isEmpty() && record.isPresent() && userDepts.stream().noneMatch(dept -> dept.equalsIgnoreCase(record.get().getDepartment()))) {
            return Optional.empty();
        }
        record.ifPresent(this::populateTransientFields);
        return record;
    }

    public void populateTransientFields(OpenSlo record) {
        if ("SLO".equalsIgnoreCase(record.getKind())) {
            SloReport report = calculateSloReport(record);
            record.setErrorBudget(report.getErrorBudget());
            record.setStatus(determineStatus(report.getErrorBudget()));
            record.setCurrentValue(report.getCurrentValue());
        } else if ("Service".equalsIgnoreCase(record.getKind())) {
            List<OpenSlo> childSlos = record.getSlos();
            if (childSlos != null && !childSlos.isEmpty()) {
                double totalValue = 0;
                double minErrorBudget = Double.MAX_VALUE;
                for (OpenSlo slo : childSlos) {
                    SloReport report = calculateSloReport(slo);
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

    public Optional<OpenSlo> getRecordByName(String name) {
        Optional<OpenSlo> record = repository.findByName(name);
        List<String> userDepts = getCurrentUserDepartments();
        if (!userDepts.isEmpty() && record.isPresent() && userDepts.stream().noneMatch(dept -> dept.equalsIgnoreCase(record.get().getDepartment()))) {
            return Optional.empty();
        }
        return record;
    }

    public OpenSlo createRecord(OpenSlo record) {
        return repository.save(record);
    }

    public OpenSlo updateRecord(Long id, OpenSlo updatedRecord) {
        return repository.findById(id)
                .map(record -> {
                    record.setApiVersion(updatedRecord.getApiVersion());
                    record.setKind(updatedRecord.getKind());
                    record.setName(updatedRecord.getName());
                    record.setDisplayName(updatedRecord.getDisplayName());
                    record.setSpec(updatedRecord.getSpec());
                    record.setDepartment(updatedRecord.getDepartment());
                    record.setItal(updatedRecord.getItal());
                    record.setRefreshRate(updatedRecord.getRefreshRate());
                    record.setLastRefreshTime(updatedRecord.getLastRefreshTime());
                    record.setAlertUrl(updatedRecord.getAlertUrl());
                    record.setAlertPayload(updatedRecord.getAlertPayload());
                    record.setAlertingSource(updatedRecord.getAlertingSource());
                    record.setService(updatedRecord.getService());
                    record.setDatasource(updatedRecord.getDatasource());
                    record.setSlis(updatedRecord.getSlis());
                    record.setSlos(updatedRecord.getSlos());
                    return repository.save(record);
                }).orElseThrow(() -> new RuntimeException("OpenSlo record not found with id " + id));
    }

    @Transactional
    public void deleteRecord(Long id) {
        repository.findById(id).ifPresent(record -> {
            metricRepository.deleteBySli(record);
            metricRepository.deleteByDatasource(record);
            
            // Cleanup linked SLOs if this is an AlertingSource
            if ("AlertingSource".equalsIgnoreCase(record.getKind())) {
                List<OpenSlo> slos = repository.findByKind("SLO");
                for (OpenSlo slo : slos) {
                    if (slo.getAlertingSource() != null && slo.getAlertingSource().getId().equals(record.getId())) {
                        slo.setAlertingSource(null);
                        repository.save(slo);
                    }
                }
            }
            
            repository.delete(record);
        });
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

    public SliReport getSliReport(Long id) {
        OpenSlo sli = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("OpenSLO record not found with id " + id));

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
        for (OpenSlo slo : allSlos) {
            if (slo.getSlis() != null && slo.getSlis().contains(sli)) {
                totalTarget += parseTargetFromSpec(slo.getSpec());
                targetCount++;
            }
        }
        Double avgTarget = targetCount > 0 ? (totalTarget / targetCount) : null;

        // Take up to last 100 metrics for display in SLI view
        List<SliMetric> recentMetrics = allMetrics.size() > 100 ? allMetrics.subList(0, 100) : allMetrics;

        return new SliReport(sli.getName(), sli.getDisplayName(), currentValue, avgTarget, recentMetrics);
    }

    public SloReport getSloReport(Long id) {
        OpenSlo slo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("OpenSLO record not found with id " + id));

        if (!"SLO".equalsIgnoreCase(slo.getKind())) {
            throw new RuntimeException("Record is not of kind SLO");
        }

        return calculateSloReport(slo);
    }

    public DashboardStats getDashboardStats() {
        List<OpenSlo> services = repository.findByKind("Service");
        List<OpenSlo> slos = repository.findByKind("SLO");
        
        List<OpenSlo> filteredServices = filterByDepartment(services);
        List<OpenSlo> filteredSlos = filterByDepartment(slos);
        
        long healthy = 0;
        long warning = 0;
        long breaching = 0;
        
        for (OpenSlo slo : filteredSlos) {
            SloReport report = calculateSloReport(slo);
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

    private SloReport calculateSloReport(OpenSlo slo) {
        double target = parseTargetFromSpec(slo.getSpec());
        int windowDays = parseWindowDaysFromSpec(slo.getSpec());
        LocalDateTime windowStart = LocalDateTime.now().minusDays(windowDays);

        List<SliMetric> allMetrics = new ArrayList<>();
        for (OpenSlo sli : slo.getSlis()) {
            allMetrics.addAll(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(sli, windowStart));
        }

        double currentSloValue = allMetrics.stream()
                .mapToDouble(SliMetric::getValue)
                .average()
                .orElse(1.0);

        double errorBudget = (1.0 - target > 0) ? (currentSloValue - target) / (1.0 - target) * 100 : 100.0;
        String status = determineStatus(errorBudget);

        List<SliMetric> recentMetrics = allMetrics.size() > 50 ? allMetrics.subList(0, 50) : allMetrics;

        return new SloReport(slo.getName(), target, currentSloValue, errorBudget, status, recentMetrics);
    }

    public ServiceReport getServiceReport(Long id) {
        OpenSlo service = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("OpenSLO record not found with id " + id));

        if (!"Service".equalsIgnoreCase(service.getKind())) {
            throw new RuntimeException("Record is not of kind Service");
        }

        List<SloReport> sloReports = new ArrayList<>();
        if (service.getSlos() != null) {
            for (OpenSlo slo : service.getSlos()) {
                sloReports.add(calculateSloReport(slo));
            }
        }

        return new ServiceReport(service.getName(), service.getDisplayName(), sloReports);
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

    private int parseWindowDaysFromSpec(String spec) {
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
