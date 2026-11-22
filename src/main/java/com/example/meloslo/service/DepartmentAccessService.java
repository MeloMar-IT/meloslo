package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Single place that decides which records the current caller may see and which departments
 * the caller may assign.
 */
@Service
public class DepartmentAccessService {

    private final UserRepository userRepository;

    public DepartmentAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Scope of the caller in the SecurityContext. No Authentication at all means a trusted
     * internal call (for example a scheduler) and is unrestricted.
     */
    public AccessScope currentScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return AccessScope.unrestricted();
        }
        if (!auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(auth.getPrincipal())) {
            return AccessScope.none();
        }
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return AccessScope.unrestricted();
        }
        String departments = userRepository.findByUsername(auth.getName())
                .map(User::getDepartments)
                .orElse(null);
        if (departments == null || departments.trim().isEmpty()) {
            return AccessScope.none();
        }
        return AccessScope.of(Arrays.asList(departments.split(",")));
    }

    /**
     * What one caller may access. Department names are trimmed and compared case-insensitively.
     * Immutable and safe to share across a request.
     */
    public static final class AccessScope {
        private static final AccessScope UNRESTRICTED = new AccessScope(true, Collections.emptySet());
        private static final AccessScope NONE = new AccessScope(false, Collections.emptySet());

        private final boolean unrestricted;
        private final Set<String> departments;

        private AccessScope(boolean unrestricted, Set<String> departments) {
            this.unrestricted = unrestricted;
            this.departments = departments;
        }

        /** Scope without restrictions (administrators and internal calls). */
        public static AccessScope unrestricted() {
            return UNRESTRICTED;
        }

        /** Scope that grants access to nothing (anonymous callers, no departments). */
        public static AccessScope none() {
            return NONE;
        }

        /** Scope limited to the given departments; blank names are ignored. Empty input gives {@link #none()}. */
        public static AccessScope of(Collection<String> departments) {
            Set<String> normalised = new LinkedHashSet<>();
            if (departments != null) {
                for (String d : departments) {
                    String n = normalise(d);
                    if (n != null) {
                        normalised.add(n);
                    }
                }
            }
            if (normalised.isEmpty()) {
                return NONE;
            }
            return new AccessScope(false, Collections.unmodifiableSet(normalised));
        }

        private static String normalise(String department) {
            if (department == null) {
                return null;
            }
            String t = department.trim();
            return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
        }

        /** True when the scope has no department restriction at all. */
        public boolean isUnrestricted() {
            return unrestricted;
        }

        /**
         * Whether the caller may see or change this record. A record without a department is
         * visible only to unrestricted scopes; a null record is never accessible.
         */
        public boolean canAccess(OpenSlo record) {
            if (record == null) {
                return false;
            }
            return unrestricted || departments.contains(normalise(record.getDepartment()));
        }

        /** Whether the caller may create or move a record into the given department. */
        public boolean canAssign(String department) {
            return unrestricted || departments.contains(normalise(department));
        }

        /** The normalised (trimmed, lower-case) departments of a restricted scope; empty when unrestricted. */
        public Set<String> departments() {
            return departments;
        }
    }
}
