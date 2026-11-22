package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.service.DepartmentAccessService.AccessScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSloRedactorTest {

    private final OpenSloRedactor redactor = new OpenSloRedactor();

    private static OpenSlo record(long id, String kind, String name, String department) {
        OpenSlo r = new OpenSlo("openslo/v1", kind, name, name, "{}");
        r.setId(id);
        r.setDepartment(department);
        return r;
    }

    @Test
    void restrictedScopeDropsForeignLinksAndKeepsOwn() {
        OpenSlo finSli = record(1, "SLI", "fin-sli", "Finance");
        OpenSlo infraSli = record(2, "SLI", "infra-sli", "Infrastructure");
        OpenSlo infraAlert = record(3, "AlertingSource", "infra-alert", "Infrastructure");
        OpenSlo finService = record(4, "BusinessService", "fin-service", "Finance");
        OpenSlo slo = record(5, "SLO", "slo", "Finance");
        slo.setSlis(new ArrayList<>(List.of(finSli, infraSli)));
        slo.setAlertingSource(infraAlert);
        slo.setService(finService);

        OpenSlo result = redactor.redact(slo, AccessScope.of(List.of("finance")));

        assertThat(result).isNotSameAs(slo);
        assertThat(result.getSlis()).extracting(OpenSlo::getName).containsExactly("fin-sli");
        assertThat(result.getAlertingSource()).isNull();
        assertThat(result.getService().getName()).isEqualTo("fin-service");
        assertThat(result.getName()).isEqualTo("slo");
    }

    @Test
    void originalEntityIsNotChanged() {
        OpenSlo infraSli = record(2, "SLI", "infra-sli", "Infrastructure");
        OpenSlo infraAlert = record(3, "AlertingSource", "infra-alert", "Infrastructure");
        OpenSlo slo = record(5, "SLO", "slo", "Finance");
        slo.setSlis(new ArrayList<>(List.of(infraSli)));
        slo.setAlertingSource(infraAlert);

        redactor.redact(slo, AccessScope.of(List.of("Finance")));

        assertThat(slo.getSlis()).containsExactly(infraSli);
        assertThat(slo.getAlertingSource()).isSameAs(infraAlert);
    }

    @Test
    void unrestrictedScopeReturnsSameInstance() {
        OpenSlo slo = record(5, "SLO", "slo", null);

        assertThat(redactor.redact(slo, AccessScope.unrestricted())).isSameAs(slo);
        List<OpenSlo> list = List.of(slo);
        assertThat(redactor.redactAll(list, AccessScope.unrestricted())).isSameAs(list);
    }

    @Test
    void redactedServiceKeepsOnlyVisibleChildSlos() {
        OpenSlo service = record(1, "BusinessService", "svc", "Finance");
        service.setSlos(new ArrayList<>(List.of(
                record(2, "SLO", "fin-child", "Finance"), record(3, "SLO", "hr-child", "HR"))));

        OpenSlo result = redactor.redact(service, AccessScope.of(List.of("Finance")));

        assertThat(result.getSlos()).extracting(OpenSlo::getName).containsExactly("fin-child");
        assertThat(service.getSlos()).hasSize(2);
    }
}
