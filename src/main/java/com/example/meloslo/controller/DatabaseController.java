package com.example.meloslo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/database")
public class DatabaseController {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DatabaseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/query")
    public List<Map<String, Object>> executeQuery(@RequestBody QueryRequest request) {
        String sql = request.getSql();
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be empty");
        }
        
        String cleanSql = sql.trim().toUpperCase();
        
        // Block dangerous keywords even within strings to be safe (very basic)
        if (cleanSql.contains("DELETE") || cleanSql.contains("DROP") || cleanSql.contains("UPDATE") || 
            cleanSql.contains("INSERT") || cleanSql.contains("TRUNCATE") || cleanSql.contains("ALTER")) {
             throw new IllegalArgumentException("Only SELECT queries are allowed for security reasons.");
        }

        if (!cleanSql.startsWith("SELECT")) {
             throw new IllegalArgumentException("Only SELECT queries are allowed.");
        }

        return jdbcTemplate.queryForList(sql);
    }

    @GetMapping("/tables")
    public List<String> getTables() {
        return jdbcTemplate.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE IN ('TABLE', 'BASE TABLE')",
            String.class
        );
    }

    @GetMapping("/tables/{tableName}/structure")
    public List<Map<String, Object>> getTableStructure(@PathVariable String tableName) {
        // Basic validation to prevent SQL injection in table name
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid table name");
        }
        
        return jdbcTemplate.queryForList(
            "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT " +
            "FROM INFORMATION_SCHEMA.COLUMNS " +
            "WHERE TABLE_NAME = ? AND TABLE_SCHEMA = 'PUBLIC' " +
            "ORDER BY ORDINAL_POSITION",
            tableName.toUpperCase()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, String> response = new HashMap<>();
        response.put("message", e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    public static class QueryRequest {
        private String sql;

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }
    }
}
