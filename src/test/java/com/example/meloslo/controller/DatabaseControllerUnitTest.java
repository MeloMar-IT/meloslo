package com.example.meloslo.controller;

import com.example.meloslo.repository.UserRepository;
import com.example.meloslo.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DatabaseController.class)
@Import({SecurityConfig.class, com.example.meloslo.CsrfTestConfig.class})
@WithMockUser(roles = "ADMIN")
public class DatabaseControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT FILE_READ('/etc/passwd')",
        "SELECT CSVWRITE('/tmp/meloslo.csv','SELECT 1')",
        "CREATE ALIAS EXEC AS 'String e(String c) throws Exception { return c; }'"
    })
    void queryEndpointIsGoneAndRunsNoSql(String sql) throws Exception {
        mockMvc.perform(post("/api/v1/database/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("sql", sql))))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldReturnTableList() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("OPENSLO_RECORDS", "SLI_METRIC"));

        mockMvc.perform(get("/api/v1/database/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("OPENSLO_RECORDS"))
                .andExpect(jsonPath("$[1]").value("SLI_METRIC"));
    }

    @Test
    void shouldReturnTableStructure() throws Exception {
        Map<String, Object> column = Map.of(
                "COLUMN_NAME", "NAME",
                "DATA_TYPE", "VARCHAR",
                "IS_NULLABLE", "NO"
        );
        when(jdbcTemplate.queryForList(anyString(), eq("OPENSLO_RECORDS")))
                .thenReturn(List.of(column));

        mockMvc.perform(get("/api/v1/database/tables/OPENSLO_RECORDS/structure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].COLUMN_NAME").value("NAME"));
    }

    @Test
    void shouldRejectInvalidTableName() throws Exception {
        mockMvc.perform(get("/api/v1/database/tables/INVALID@TABLE/structure"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldDenyAccessToNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/database/tables"))
                .andExpect(status().isForbidden());
    }
}
