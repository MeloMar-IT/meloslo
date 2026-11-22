package com.example.meloslo.controller;

import com.example.meloslo.model.SliMetric;
import com.example.meloslo.service.InvalidSliValueException;
import com.example.meloslo.service.OpenSloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {

    private final OpenSloService service;

    @Autowired
    public MetricController(OpenSloService service) {
        this.service = service;
    }

    @GetMapping
    public List<SliMetric> getAllMetrics() {
        return service.getAllMetrics();
    }

    @PostMapping
    public ResponseEntity<SliMetric> createMetric(@RequestBody SliMetric metric) {
        SliMetric created = service.createMetric(metric);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /** Maps a refused metric value to 400 Bad Request with a {@code message} field. */
    @ExceptionHandler(InvalidSliValueException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSliValue(InvalidSliValueException e) {
        return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.BAD_REQUEST);
    }
}
