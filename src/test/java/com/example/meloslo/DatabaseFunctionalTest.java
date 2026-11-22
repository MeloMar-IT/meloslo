package com.example.meloslo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT FILE_READ('/etc/passwd')",
        "SELECT CSVWRITE('/tmp/meloslo.csv','SELECT 1')",
        "CREATE ALIAS EXEC AS 'String e(String c) throws Exception { return c; }'"
    })
    void queryEndpointIsGone(String sql) {
        Map<String, String> request = new HashMap<>();
        request.put("sql", sql);

        ResponseEntity<String> response = adminAuth().postForEntity("/api/v1/database/query", request, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        if (response.getBody() != null) {
            assertThat(response.getBody()).doesNotContain("root:");
        }
    }

    @Test
    void shouldReturnForbiddenForRegularUser() {
        ResponseEntity<Object> response = userAuth().getForEntity("/api/v1/database/tables", Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
