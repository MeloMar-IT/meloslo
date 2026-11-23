package com.example.meloslo.controller;

import com.example.meloslo.dto.DashboardStats;
import com.example.meloslo.dto.BusinessServiceReport;
import com.example.meloslo.dto.SliReport;
import com.example.meloslo.dto.SloReport;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.service.InvalidSliValueException;
import com.example.meloslo.service.OpenSloService;
import com.example.meloslo.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/openslo")
public class OpenSloController {

    private final OpenSloService service;

    @Autowired
    public OpenSloController(OpenSloService service) {
        this.service = service;
    }

    @GetMapping
    public List<OpenSlo> getAllRecords(@RequestParam(required = false) String kind) {
        if (kind != null) {
            return service.getRecordsByKind(kind);
        }
        return service.getAllRecords();
    }

    @GetMapping("/dashboard-stats")
    public DashboardStats getDashboardStats() {
        return service.getDashboardStats();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OpenSlo> getRecordById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRecordById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OpenSLO record not found with id " + id)));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<SloReport> getSloReport(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSloReport(id));
    }

    @GetMapping("/{id}/service-report")
    public ResponseEntity<BusinessServiceReport> getServiceReport(@PathVariable Long id) {
        return ResponseEntity.ok(service.getServiceReport(id));
    }

    @GetMapping("/{id}/sli-report")
    public ResponseEntity<SliReport> getSliReport(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSliReport(id));
    }

    @PostMapping
    public ResponseEntity<OpenSlo> createRecord(@Valid @RequestBody OpenSlo record) {
        return new ResponseEntity<>(service.createRecord(record), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OpenSlo> updateRecord(@PathVariable Long id, @Valid @RequestBody OpenSlo record) {
        return ResponseEntity.ok(service.updateRecord(id, record));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable Long id) {
        service.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }

    /** Maps a refused department access to 403. Kept local so Spring Security's 401 for anonymous callers stays intact. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiExceptionHandler.ApiError> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiExceptionHandler.ApiError.of(HttpStatus.FORBIDDEN, e.getMessage()));
    }

    /** Maps a refused SLO target or SLI value to 400 Bad Request with a {@code message} field. */
    @ExceptionHandler(InvalidSliValueException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSliValue(InvalidSliValueException e) {
        return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.BAD_REQUEST);
    }
}
