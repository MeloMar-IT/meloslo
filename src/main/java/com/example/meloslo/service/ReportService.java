package com.example.meloslo.service;

import com.example.meloslo.dto.ReportOptionsDTO;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.repository.MetricRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYDifferenceRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.ui.Layer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.chart.ui.RectangleEdge;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final OpenSloService openSloService;
    private final MetricRepository metricRepository;

    public ReportService(OpenSloService openSloService, MetricRepository metricRepository) {
        this.openSloService = openSloService;
        this.metricRepository = metricRepository;
    }

    public byte[] generatePdfReport(ReportOptionsDTO options) {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.BLACK);
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.DARK_GRAY);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);

            // Title
            Paragraph title = new Paragraph("MeloSlo Annual Performance Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime since = calculateSinceDate(now, options);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            
            Paragraph dateInfo = new Paragraph("Reporting Period: " + since.format(formatter) + " to " + now.format(formatter), normalFont);
            dateInfo.setAlignment(Element.ALIGN_CENTER);
            dateInfo.setSpacingAfter(20);
            document.add(dateInfo);

            List<String> userDepts = openSloService.getCurrentUserDepartments();

            for (Long id : options.getIds()) {
                openSloService.getRecordById(id).ifPresent(record -> {
                    // Check access
                    boolean hasAccess = userDepts.isEmpty() || userDepts.contains(record.getDepartment());
                    if (hasAccess) {
                        try {
                            addRecordToPdf(document, record, since, now, subTitleFont, boldFont, normalFont, options);
                            document.add(new Chunk("\n"));
                        } catch (DocumentException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    private LocalDateTime calculateSinceDate(LocalDateTime now, ReportOptionsDTO options) {
        int val = options.getTimeValue();
        String unit = options.getTimeUnit() != null ? options.getTimeUnit().toUpperCase() : "YEARS";
        
        return switch (unit) {
            case "DAYS" -> now.minusDays(val);
            case "MONTHS" -> now.minusMonths(val);
            default -> now.minusYears(val);
        };
    }

    private void addRecordToPdf(Document document, OpenSlo record, LocalDateTime since, LocalDateTime now,
                                Font subTitleFont, Font boldFont, Font normalFont, ReportOptionsDTO options) throws DocumentException {
        
        Paragraph recordHeader = new Paragraph(record.getKind().toUpperCase() + ": " + record.getDisplayName(), subTitleFont);
        recordHeader.setSpacingBefore(10);
        document.add(recordHeader);
        
        if (options.isIncludeMetadata()) {
            document.add(new Paragraph("System ID: " + record.getName(), normalFont));
            document.add(new Paragraph("Department: " + record.getDepartment() + " | Manager: " + record.getManager(), normalFont));
            document.add(new Chunk("\n"));
        }

        if ("BusinessService".equalsIgnoreCase(record.getKind())) {
            List<OpenSlo> slos = record.getSlos();
            if (slos == null || slos.isEmpty()) {
                document.add(new Paragraph("No SLOs associated with this service.", normalFont));
            } else {
                if (options.isIncludeBusinessServiceAggregatedChart()) {
                    // Aggregate service metrics for a summary graph
                    List<SliMetric> allServiceMetrics = new ArrayList<>();
                    for (OpenSlo slo : slos) {
                        if (slo.getSlis() != null) {
                            for (OpenSlo sli : slo.getSlis()) {
                                allServiceMetrics.addAll(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(sli, since));
                            }
                        }
                    }

                    // Always show if the user wants 0s for missing data
                    String timeLabel = options.getTimeValue() + " " + options.getTimeUnit().toLowerCase();
                    document.add(new Paragraph("Business Service Performance Aggregation (" + timeLabel + ")", boldFont));
                    Map<LocalDateTime, Double> aggregatedData = getAggregatedDataWithZeros(allServiceMetrics, since, now, calculateChronoUnit(since, now));
                    
                    double totalTarget = 0;
                    for (OpenSlo slo : slos) {
                        totalTarget += openSloService.parseTargetFromSpec(slo.getSpec());
                    }
                    double serviceTarget = totalTarget / slos.size();
                    
                    Image serviceChart = generateChartImage(record.getDisplayName() + " Aggregated Performance", aggregatedData, serviceTarget, null, "Performance (%)");
                    if (serviceChart != null) {
                        serviceChart.scaleToFit(500, 250);
                        serviceChart.setAlignment(Image.ALIGN_CENTER);
                        document.add(serviceChart);
                    }
                    document.add(new Chunk("\n"));
                }

                for (OpenSlo slo : slos) {
                    addSloPerformance(document, slo, since, now, boldFont, normalFont, options);
                }
            }
        } else if ("SLO".equalsIgnoreCase(record.getKind())) {
            addSloPerformance(document, record, since, now, boldFont, normalFont, options);
        }
    }

    private void addSloPerformance(Document document, OpenSlo slo, LocalDateTime since, LocalDateTime now,
                                   Font boldFont, Font normalFont, ReportOptionsDTO options) throws DocumentException {
        
        document.add(new Paragraph("SLO Performance: " + slo.getDisplayName(), boldFont));
        
        double target = openSloService.parseTargetFromSpec(slo.getSpec());
        
        List<SliMetric> allMetrics = new ArrayList<>();
        if (slo.getSlis() != null) {
            for (OpenSlo sli : slo.getSlis()) {
                allMetrics.addAll(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(sli, since));
            }
        }

        // Fill gaps with 0.0 as requested
        ChronoUnit unit = calculateChronoUnit(since, now);
        Map<LocalDateTime, Double> chartData = getAggregatedDataWithZeros(allMetrics, since, now, unit);

        // Aggregated values
        double avgValue = chartData.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        
        double errorBudget = (1.0 - target > 0) ? (avgValue - target) / (1.0 - target) * 100 : 100.0;

        if (options.isIncludeSloDetails()) {
            String statusText = "BREACHING";
            Color statusColor = Color.RED;
            if (errorBudget >= 25) {
                statusText = "HEALTHY";
                statusColor = new Color(0, 128, 0);
            } else if (errorBudget >= 0) {
                statusText = "WARNING";
                statusColor = new Color(255, 165, 0);
            }

            Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, statusColor);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(5);
            table.setSpacingAfter(5);

            table.addCell(new PdfPCell(new Phrase("Metric", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Value", boldFont)));

            if (options.isIncludeTarget()) {
                table.addCell("Target");
                table.addCell(String.format("%.4f%%", target * 100));
            }

            if (options.isIncludeAvgPerformance()) {
                String timeLabel = options.getTimeValue() + " " + options.getTimeUnit().toLowerCase();
                table.addCell("Average Performance (" + timeLabel + ")");
                table.addCell(String.format("%.4f%%", avgValue * 100));
            }

            if (options.isIncludeErrorBudget()) {
                table.addCell("Error Budget Remaining");
                table.addCell(String.format("%.2f%%", errorBudget));
            }

            if (options.isIncludeStatus()) {
                table.addCell("Status");
                PdfPCell statusCell = new PdfPCell(new Phrase(statusText, statusFont));
                table.addCell(statusCell);
            }

            document.add(table);
        }

        // Add Chart
        if (options.isIncludeSloCharts()) {
            if (options.isIncludeMetricsChart()) {
                Image chartImage = generateChartImage(slo.getDisplayName() + " Performance", chartData, target, null, "Performance (%)");
                if (chartImage != null) {
                    chartImage.scaleToFit(500, 250);
                    chartImage.setAlignment(Image.ALIGN_CENTER);
                    document.add(chartImage);
                }
            }

            if (options.isIncludeErrorBudgetChart()) {
                Map<LocalDateTime, Double> ebData = new TreeMap<>();
                chartData.forEach((dt, val) -> {
                    double eb = (1.0 - target > 0) ? (val - target) / (1.0 - target) * 100 : 100.0;
                    ebData.put(dt, eb);
                });
                
                // Calculate Trend
                List<Double> trendPoints = openSloService.calculateTrendPoints(allMetrics, target, 30);
                Map<LocalDateTime, Double> trendData = new TreeMap<>();
                if (!trendPoints.isEmpty()) {
                    LocalDateTime lastDate = ((TreeMap<LocalDateTime, Double>)ebData).lastKey();
                    trendData.put(lastDate, ebData.get(lastDate));
                    for (int i = 0; i < trendPoints.size(); i++) {
                        trendData.put(lastDate.plusDays(i + 1), trendPoints.get(i));
                    }
                }

                Image ebChartImage = generateChartImage(slo.getDisplayName() + " Error Budget Remaining (%)", ebData, 0.0, trendData, "Error Budget (%)");
                if (ebChartImage != null) {
                    ebChartImage.scaleToFit(500, 250);
                    ebChartImage.setAlignment(Image.ALIGN_CENTER);
                    document.add(ebChartImage);
                }
            }
        }
        
        document.add(new Chunk("\n"));
    }

    private Map<LocalDateTime, Double> getAggregatedDataWithZeros(List<SliMetric> metrics, 
                                                                  LocalDateTime since, 
                                                                  LocalDateTime now, 
                                                                  ChronoUnit unit) {
        // First aggregate existing metrics
        Map<LocalDateTime, Double> aggregated = metrics.stream()
                .collect(Collectors.groupingBy(
                        sm -> truncateTo(sm.getTimestamp(), unit),
                        Collectors.averagingDouble(SliMetric::getValue)
                ));
        
        // Then fill missing slots with 0.0
        LocalDateTime current = truncateTo(since, unit);
        LocalDateTime end = truncateTo(now, unit);
        while (current.isBefore(end) || current.isEqual(end)) {
            aggregated.putIfAbsent(current, 0.0);
            current = current.plus(1, unit);
        }
        return aggregated;
    }

    private LocalDateTime truncateTo(LocalDateTime ldt, ChronoUnit unit) {
        if (unit == ChronoUnit.WEEKS) {
            return ldt.truncatedTo(ChronoUnit.DAYS).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        }
        if (unit == ChronoUnit.DAYS || unit == ChronoUnit.HOURS || unit == ChronoUnit.MINUTES) {
            return ldt.truncatedTo(unit);
        }
        // Fallback or other units if needed (not expected for now)
        return ldt.truncatedTo(ChronoUnit.DAYS);
    }

    private ChronoUnit calculateChronoUnit(LocalDateTime since, LocalDateTime now) {
        long days = ChronoUnit.DAYS.between(since, now);
        if (days <= 2) return ChronoUnit.HOURS;
        if (days <= 60) return ChronoUnit.DAYS;
        return ChronoUnit.WEEKS;
    }

    private Image generateChartImage(String title, Map<LocalDateTime, Double> dataPoints, Double target, Map<LocalDateTime, Double> trendData, String yAxisLabel) {
        if (dataPoints == null || dataPoints.isEmpty()) return null;

        TimeSeries series = new TimeSeries("Data");
        TimeSeries targetSeries = new TimeSeries("Target");
        TimeSeries baseSeries = new TimeSeries("Base");
        
        TreeMap<LocalDateTime, Double> sortedData = new TreeMap<>(dataPoints);

        for (Map.Entry<LocalDateTime, Double> entry : sortedData.entrySet()) {
            Date date = Date.from(entry.getKey().atZone(ZoneId.systemDefault()).toInstant());
            double val = entry.getValue();
            if ("Performance (%)".equals(yAxisLabel)) val *= 100;
            
            series.addOrUpdate(new Second(date), val);
            if (target != null) {
                double t = target;
                if ("Performance (%)".equals(yAxisLabel)) t *= 100;
                targetSeries.addOrUpdate(new Second(date), t);
                baseSeries.addOrUpdate(new Second(date), Math.min(val, t));
            }
        }

        JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", yAxisLabel, null, true, true, false);
        XYPlot plot = chart.getXYPlot();

        if ("Performance (%)".equals(yAxisLabel) && target != null) {
            TimeSeriesCollection diffDataset = new TimeSeriesCollection();
            diffDataset.addSeries(series);
            diffDataset.addSeries(targetSeries);
            
            TimeSeriesCollection baseDataset = new TimeSeriesCollection();
            baseDataset.addSeries(baseSeries);
            
            plot.setDataset(0, baseDataset);
            XYAreaRenderer areaRenderer = new XYAreaRenderer(XYAreaRenderer.AREA);
            areaRenderer.setSeriesPaint(0, new Color(239, 68, 68, 128)); // Semi-transparent Red
            plot.setRenderer(0, areaRenderer);
            
            plot.setDataset(1, diffDataset);
            XYDifferenceRenderer diffRenderer = new XYDifferenceRenderer(
                    new Color(16, 185, 129, 128), // Positive: Semi-transparent Green
                    new Color(0, 0, 0, 0),        // Negative: Transparent
                    false
            );
            diffRenderer.setSeriesPaint(0, new Color(59, 130, 246)); // Perf line: Blue
            diffRenderer.setSeriesPaint(1, new Color(16, 185, 129)); // Target line: Green
            plot.setRenderer(1, diffRenderer);
        } else {
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            plot.setDataset(0, dataset);
            
            XYAreaRenderer areaRenderer = new XYAreaRenderer(XYAreaRenderer.AREA_AND_SHAPES);
            if ("Error Budget (%)".equals(yAxisLabel)) {
                areaRenderer.setSeriesPaint(0, new Color(59, 130, 246, 128)); // Blue
            } else {
                areaRenderer.setSeriesPaint(0, new Color(16, 185, 129, 128)); // Green
            }
            plot.setRenderer(0, areaRenderer);

            if (target != null) {
                double t = target;
                if ("Performance (%)".equals(yAxisLabel)) t *= 100;
                ValueMarker marker = new ValueMarker(t);
                marker.setPaint(new Color(16, 185, 129));
                marker.setStroke(new java.awt.BasicStroke(2.0f));
                marker.setLabel(yAxisLabel.contains("Budget") ? "Breach Threshold" : "Target");
                marker.setLabelAnchor(org.jfree.chart.ui.RectangleAnchor.TOP_RIGHT);
                marker.setLabelTextAnchor(org.jfree.chart.ui.TextAnchor.BOTTOM_RIGHT);
                plot.addRangeMarker(marker);
            }
        }

        // Add Trend if available
        if (trendData != null && !trendData.isEmpty()) {
            TimeSeries trendSeries = new TimeSeries("30-Day Trend");
            trendData.forEach((dt, val) -> {
                Date date = Date.from(dt.atZone(ZoneId.systemDefault()).toInstant());
                trendSeries.addOrUpdate(new Second(date), val);
            });
            TimeSeriesCollection trendDataset = new TimeSeriesCollection();
            trendDataset.addSeries(trendSeries);
            int idx = plot.getDatasetCount();
            plot.setDataset(idx, trendDataset);
            XYLineAndShapeRenderer trendRenderer = new XYLineAndShapeRenderer(true, false);
            trendRenderer.setSeriesPaint(0, new Color(245, 158, 11)); // Amber/Orange for trend
            trendRenderer.setSeriesStroke(0, new java.awt.BasicStroke(2.0f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND, 1.0f, new float[]{6.0f, 6.0f}, 0.0f));
            plot.setRenderer(idx, trendRenderer);
        }

        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        long days = ChronoUnit.DAYS.between(sortedData.firstKey(), sortedData.lastKey());
        if (days < 2) {
            axis.setDateFormatOverride(new java.text.SimpleDateFormat("HH:mm"));
        } else if (days < 30) {
            axis.setDateFormatOverride(new java.text.SimpleDateFormat("MMM dd"));
        } else {
            axis.setDateFormatOverride(new java.text.SimpleDateFormat("MMM yyyy"));
        }

        if (chart.getLegend() != null) {
            chart.getLegend().setPosition(RectangleEdge.BOTTOM);
        }

        BufferedImage bufferedImage = chart.createBufferedImage(800, 400);
        try {
            return Image.getInstance(bufferedImage, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
