package com.example.meloslo.service;

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
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
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

    public byte[] generatePdfReport(List<Long> ids) {
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
            LocalDateTime lastYear = now.minusYears(1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            
            Paragraph dateInfo = new Paragraph("Reporting Period: " + lastYear.format(formatter) + " to " + now.format(formatter), normalFont);
            dateInfo.setAlignment(Element.ALIGN_CENTER);
            dateInfo.setSpacingAfter(20);
            document.add(dateInfo);

            List<String> userDepts = openSloService.getCurrentUserDepartments();

            for (Long id : ids) {
                openSloService.getRecordById(id).ifPresent(record -> {
                    // Check access
                    boolean hasAccess = userDepts.isEmpty() || userDepts.contains(record.getDepartment());
                    if (hasAccess) {
                        try {
                            addRecordToPdf(document, record, lastYear, now, subTitleFont, boldFont, normalFont);
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

    private void addRecordToPdf(Document document, OpenSlo record, LocalDateTime since, LocalDateTime now,
                                Font subTitleFont, Font boldFont, Font normalFont) throws DocumentException {
        
        Paragraph recordHeader = new Paragraph(record.getKind().toUpperCase() + ": " + record.getDisplayName(), subTitleFont);
        recordHeader.setSpacingBefore(10);
        document.add(recordHeader);
        
        document.add(new Paragraph("System ID: " + record.getName(), normalFont));
        document.add(new Paragraph("Department: " + record.getDepartment() + " | Manager: " + record.getManager(), normalFont));
        document.add(new Chunk("\n"));

        if ("Service".equalsIgnoreCase(record.getKind())) {
            List<OpenSlo> slos = record.getSlos();
            if (slos == null || slos.isEmpty()) {
                document.add(new Paragraph("No SLOs associated with this service.", normalFont));
            } else {
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
                document.add(new Paragraph("Service Performance Aggregation (1 Year)", boldFont));
                Map<LocalDateTime, Double> aggregatedData = getAggregatedDataWithZeros(allServiceMetrics, since, now, ChronoUnit.DAYS);
                
                double totalTarget = 0;
                for (OpenSlo slo : slos) {
                    totalTarget += openSloService.parseTargetFromSpec(slo.getSpec());
                }
                double serviceTarget = totalTarget / slos.size();
                
                Image serviceChart = generateChartImage(record.getDisplayName() + " Aggregated Performance", aggregatedData, serviceTarget);
                if (serviceChart != null) {
                    serviceChart.scaleToFit(500, 250);
                    serviceChart.setAlignment(Image.ALIGN_CENTER);
                    document.add(serviceChart);
                }
                document.add(new Chunk("\n"));

                for (OpenSlo slo : slos) {
                    addSloPerformance(document, slo, since, now, boldFont, normalFont);
                }
            }
        } else if ("SLO".equalsIgnoreCase(record.getKind())) {
            addSloPerformance(document, record, since, now, boldFont, normalFont);
        }
    }

    private void addSloPerformance(Document document, OpenSlo slo, LocalDateTime since, LocalDateTime now,
                                   Font boldFont, Font normalFont) throws DocumentException {
        
        document.add(new Paragraph("SLO Performance: " + slo.getDisplayName(), boldFont));
        
        double target = openSloService.parseTargetFromSpec(slo.getSpec());
        
        List<SliMetric> allMetrics = new ArrayList<>();
        if (slo.getSlis() != null) {
            for (OpenSlo sli : slo.getSlis()) {
                allMetrics.addAll(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(sli, since));
            }
        }

        // Fill gaps with 0.0 as requested
        Map<LocalDateTime, Double> chartData = getAggregatedDataWithZeros(allMetrics, since, now, ChronoUnit.HOURS);

        // Aggregated values
        double avgValue = chartData.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        
        double errorBudget = (1.0 - target > 0) ? (avgValue - target) / (1.0 - target) * 100 : 100.0;
        
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

        table.addCell("Target");
        table.addCell(String.format("%.4f%%", target * 100));

        table.addCell("Average Performance (1 Year)");
        table.addCell(String.format("%.4f%%", avgValue * 100));

        table.addCell("Error Budget Remaining");
        table.addCell(String.format("%.2f%%", errorBudget));

        table.addCell("Status");
        PdfPCell statusCell = new PdfPCell(new Phrase(statusText, statusFont));
        table.addCell(statusCell);

        document.add(table);

        // Add Chart
        Image chartImage = generateChartImage(slo.getDisplayName() + " Performance", chartData, target);
        if (chartImage != null) {
            chartImage.scaleToFit(500, 250);
            chartImage.setAlignment(Image.ALIGN_CENTER);
            document.add(chartImage);
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
                        sm -> sm.getTimestamp().truncatedTo(unit),
                        Collectors.averagingDouble(SliMetric::getValue)
                ));
        
        // Then fill missing slots with 0.0
        LocalDateTime current = since.truncatedTo(unit);
        LocalDateTime end = now.truncatedTo(unit);
        while (current.isBefore(end) || current.isEqual(end)) {
            aggregated.putIfAbsent(current, 0.0);
            current = current.plus(1, unit);
        }
        return aggregated;
    }

    private Image generateChartImage(String title, Map<LocalDateTime, Double> dataPoints, Double target) {
        if (dataPoints == null || dataPoints.isEmpty()) return null;

        TimeSeries series = new TimeSeries("Performance");
        TimeSeries targetSeries = new TimeSeries("Target");
        TimeSeries baseSeries = new TimeSeries("Base");
        
        // Use TreeMap to keep timestamps sorted
        TreeMap<LocalDateTime, Double> sortedData = new TreeMap<>(dataPoints);

        for (Map.Entry<LocalDateTime, Double> entry : sortedData.entrySet()) {
            Date date = Date.from(entry.getKey().atZone(ZoneId.systemDefault()).toInstant());
            double val = entry.getValue() * 100;
            series.addOrUpdate(new Second(date), val);
            if (target != null) {
                double t = target * 100;
                targetSeries.addOrUpdate(new Second(date), t);
                baseSeries.addOrUpdate(new Second(date), Math.min(val, t));
            }
        }

        JFreeChart chart;
        if (target != null) {
            TimeSeriesCollection diffDataset = new TimeSeriesCollection();
            diffDataset.addSeries(series);
            diffDataset.addSeries(targetSeries);
            
            TimeSeriesCollection baseDataset = new TimeSeriesCollection();
            baseDataset.addSeries(baseSeries);
            
            chart = ChartFactory.createTimeSeriesChart(title, "Date", "Performance (%)", null, true, true, false);
            XYPlot plot = chart.getXYPlot();
            
            // Layer 0: Base area (RED) from 0 to min(Perf, Target)
            plot.setDataset(0, baseDataset);
            XYAreaRenderer areaRenderer = new XYAreaRenderer(XYAreaRenderer.AREA);
            areaRenderer.setSeriesPaint(0, new Color(239, 68, 68, 128)); // Semi-transparent Red
            plot.setRenderer(0, areaRenderer);
            
            // Layer 1: Difference area (GREEN for delta above target)
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
            chart = ChartFactory.createTimeSeriesChart(title, "Date", "Performance (%)", dataset, false, true, false);
            XYPlot plot = chart.getXYPlot();
            XYAreaRenderer areaRenderer = new XYAreaRenderer(XYAreaRenderer.AREA_AND_SHAPES);
            areaRenderer.setSeriesPaint(0, new Color(59, 130, 246, 128));
            plot.setRenderer(areaRenderer);
        }

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new java.text.SimpleDateFormat("MMM yyyy"));

        BufferedImage bufferedImage = chart.createBufferedImage(800, 400);
        try {
            return Image.getInstance(bufferedImage, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
