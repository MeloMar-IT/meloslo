package com.example.meloslo.util;

import com.example.meloslo.model.OpenSlo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskedUrlSerializerTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void masksPathAndKeepsHostAndPort() {
        assertThat(MaskedUrlSerializer.mask("https://hooks.example.test/services/SECRETPATH"))
                .isEqualTo("https://hooks.example.test/****");
        assertThat(MaskedUrlSerializer.mask("http://hooks.example.test:8443/a?token=SECRET"))
                .isEqualTo("http://hooks.example.test:8443/****");
    }

    @Test
    void urlWithoutHostOrUnparsableBecomesBareMask() {
        assertThat(MaskedUrlSerializer.mask("mailto:x@example.invalid")).isEqualTo("****");
        assertThat(MaskedUrlSerializer.mask("not a url at all")).isEqualTo("****");
        assertThat(MaskedUrlSerializer.mask(null)).isNull();
    }

    @Test
    void isMaskedRecognisesBothForms() {
        assertThat(MaskedUrlSerializer.isMasked("****")).isTrue();
        assertThat(MaskedUrlSerializer.isMasked("https://hooks.example.test/****")).isTrue();
        assertThat(MaskedUrlSerializer.isMasked("https://hooks.example.test/hook")).isFalse();
        assertThat(MaskedUrlSerializer.isMasked(null)).isFalse();
    }

    @Test
    void topLevelAndNestedAlertUrlsAreMaskedInJson() throws Exception {
        OpenSlo source = new OpenSlo("openslo/v1", "AlertingSource", "src", "Src", "{}");
        source.setAlertUrl("https://hooks.example.test/services/SECRETPATH");
        OpenSlo slo = new OpenSlo("openslo/v1", "SLO", "slo", "Slo", "{}");
        slo.setAlertUrl("https://other.example.test/SECRETPATH");
        slo.setAlertingSource(source);

        String json = mapper.writeValueAsString(slo);
        JsonNode node = mapper.readTree(json);

        assertThat(node.get("alertUrl").asText()).isEqualTo("https://other.example.test/****");
        assertThat(node.get("alertingSource").get("alertUrl").asText()).isEqualTo("https://hooks.example.test/****");
        assertThat(json).doesNotContain("SECRETPATH");
    }

    @Test
    void deserializingKeepsTheRawValue() throws Exception {
        OpenSlo slo = mapper.readValue("{\"kind\":\"AlertingSource\",\"alertUrl\":\"https://hooks.example.test/raw\"}", OpenSlo.class);

        assertThat(slo.getAlertUrl()).isEqualTo("https://hooks.example.test/raw");
    }
}
