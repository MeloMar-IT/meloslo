package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.service.DepartmentAccessService.AccessScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds response copies of records for restricted callers. Linked records the caller cannot
 * see are left out. Managed entities are never modified; the copies must never be saved.
 */
@Component
public class OpenSloRedactor {

    /** Response copy of a record with linked records outside the scope removed; the record itself if unrestricted. */
    public OpenSlo redact(OpenSlo record, AccessScope scope) {
        if (record == null || scope.isUnrestricted()) {
            return record;
        }
        OpenSlo copy = shallowCopy(record);
        copy.setService(visible(record.getService(), scope));
        copy.setDatasource(visible(record.getDatasource(), scope));
        copy.setAlertingSource(visible(record.getAlertingSource(), scope));
        copy.setSlis(visibleList(record.getSlis(), scope));
        copy.setSlos(visibleList(record.getSlos(), scope));
        copy.setIndicatorSlis(visibleList(record.getIndicatorSlis(), scope));
        copy.setLinkedSlos(visibleList(record.getLinkedSlos(), scope));
        return copy;
    }

    /** {@link #redact(OpenSlo, AccessScope)} applied to each record; the list itself if unrestricted. */
    public List<OpenSlo> redactAll(List<OpenSlo> records, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return records;
        }
        List<OpenSlo> result = new ArrayList<>(records.size());
        for (OpenSlo r : records) {
            result.add(redact(r, scope));
        }
        return result;
    }

    /** Copy of a linked record for a restricted scope, or null when the caller cannot see it. */
    public OpenSlo redactLinked(OpenSlo linked, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return linked;
        }
        return visible(linked, scope);
    }

    /**
     * Metric copies whose sli and datasource are redacted, so a metric never exposes a
     * linked record the caller cannot see. Unrestricted scopes get the list back as is.
     */
    public List<SliMetric> redactMetrics(List<SliMetric> metrics, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return metrics;
        }
        List<SliMetric> result = new ArrayList<>(metrics.size());
        for (SliMetric m : metrics) {
            SliMetric copy = new SliMetric();
            copy.setId(m.getId());
            copy.setTimestamp(m.getTimestamp());
            copy.setValue(m.getValue());
            copy.setSli(redactLinked(m.getSli(), scope));
            copy.setDatasource(redactLinked(m.getDatasource(), scope));
            result.add(copy);
        }
        return result;
    }

    /** A linked record copy whose own SLI links are limited to visible records too. */
    private OpenSlo visible(OpenSlo linked, AccessScope scope) {
        if (linked == null || !scope.canAccess(linked)) {
            return null;
        }
        OpenSlo copy = shallowCopy(linked);
        copy.setSlis(shallowVisible(linked.getSlis(), scope));
        return copy;
    }

    private List<OpenSlo> visibleList(List<OpenSlo> linked, AccessScope scope) {
        if (linked == null) {
            return new ArrayList<>();
        }
        return linked.stream().map(l -> visible(l, scope)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private List<OpenSlo> shallowVisible(List<OpenSlo> linked, AccessScope scope) {
        if (linked == null) {
            return new ArrayList<>();
        }
        return linked.stream().filter(scope::canAccess).map(this::shallowCopy).collect(Collectors.toList());
    }

    private OpenSlo shallowCopy(OpenSlo r) {
        OpenSlo c = new OpenSlo();
        c.setId(r.getId());
        c.setApiVersion(r.getApiVersion());
        c.setKind(r.getKind());
        c.setName(r.getName());
        c.setDisplayName(r.getDisplayName());
        c.setSpec(r.getSpec());
        c.setDepartment(r.getDepartment());
        c.setManager(r.getManager());
        c.setRefreshRate(r.getRefreshRate());
        c.setLastRefreshTime(r.getLastRefreshTime());
        c.setAlertUrl(r.getAlertUrl());
        c.setAlertPayload(r.getAlertPayload());
        c.setLastAlertTime(r.getLastAlertTime());
        c.setErrorBudget(r.getErrorBudget());
        c.setStatus(r.getStatus());
        c.setCurrentValue(r.getCurrentValue());
        return c;
    }
}
