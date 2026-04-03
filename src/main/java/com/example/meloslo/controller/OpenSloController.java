package com.example.meloslo.controller;

import com.example.meloslo.dto.DashboardStats;
import com.example.meloslo.dto.ServiceReport;
import com.example.meloslo.dto.SliReport;
import com.example.meloslo.dto.SloReport;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.service.OpenSloService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return service.getRecordById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<SloReport> getSloReport(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.getSloReport(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/service-report")
    public ResponseEntity<ServiceReport> getServiceReport(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.getServiceReport(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/sli-report")
    public ResponseEntity<SliReport> getSliReport(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.getSliReport(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<OpenSlo> createRecord(@Valid @RequestBody OpenSlo record) {
        OpenSlo created = service.createRecord(record);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OpenSlo> updateRecord(@PathVariable Long id, @Valid @RequestBody OpenSlo record) {
        try {
            OpenSlo updated = service.updateRecord(id, record);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable Long id) {
        service.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }
}
