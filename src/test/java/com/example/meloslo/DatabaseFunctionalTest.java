package com.example.meloslo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseFunctionalTest extends BaseFunctionalTest {

    @Test
    void shouldListTables() {
        ResponseEntity<List> response = adminAuth().getForEntity("/api/v1/database/tables", List.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("MELO_USERS");
    }

    @Test
    void shouldExecuteSelectQuery() {
        Map<String, String> request = new HashMap<>();
        request.put("sql", "SELECT * FROM MELO_USERS");
        
        ResponseEntity<List> response = adminAuth().postForEntity("/api/v1/database/query", request, List.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void shouldRejectNonSelectQuery() {
        Map<String, String> request = new HashMap<>();
        request.put("sql", "DELETE FROM MELO_USERS");
        
        ResponseEntity<Map> response = adminAuth().postForEntity("/api/v1/database/query", request, Map.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).isEqualTo("Only SELECT queries are allowed for security reasons.");
    }

    @Test
    void shouldReturnForbiddenForRegularUser() {
        ResponseEntity<Object> response = userAuth().getForEntity("/api/v1/database/tables", Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
