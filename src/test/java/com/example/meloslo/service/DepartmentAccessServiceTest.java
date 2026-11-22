package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.UserRepository;
import com.example.meloslo.service.DepartmentAccessService.AccessScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepartmentAccessServiceTest {

    private UserRepository userRepository;
    private DepartmentAccessService access;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        access = new DepartmentAccessService(userRepository);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static OpenSlo withDepartment(String department) {
        OpenSlo r = new OpenSlo("openslo/v1", "SLO", "n", "N", "{}");
        r.setDepartment(department);
        return r;
    }

    private void login(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "pass", List.of(new SimpleGrantedAuthority(role))));
    }

    private void userWithDepartments(String departments) {
        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(new User("testuser", "pass", "t@test.com", departments)));
    }

    @Test
    void noAuthenticationIsUnrestricted() {
        AccessScope scope = access.currentScope();

        assertThat(scope.isUnrestricted()).isTrue();
        assertThat(scope.canAccess(withDepartment(null))).isTrue();
    }

    @Test
    void anonymousTokenGetsNoAccess() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        AccessScope scope = access.currentScope();

        assertThat(scope.isUnrestricted()).isFalse();
        assertThat(scope.canAccess(withDepartment("Finance"))).isFalse();
    }

    @Test
    void adminIsUnrestrictedWithoutUserLookup() {
        login("admin", "ROLE_ADMIN");

        AccessScope scope = access.currentScope();

        assertThat(scope.isUnrestricted()).isTrue();
        assertThat(scope.canAccess(withDepartment(null))).isTrue();
        assertThat(scope.canAssign(null)).isTrue();
        verify(userRepository, never()).findByUsername(Mockito.anyString());
    }

    @Test
    void userDepartmentsAreTrimmedAndMatchedIgnoringCase() {
        login("testuser", "ROLE_USER");
        userWithDepartments(" Finance , Engineering ,");

        AccessScope scope = access.currentScope();

        assertThat(scope.departments()).containsExactlyInAnyOrder("finance", "engineering");
        assertThat(scope.canAccess(withDepartment("FINANCE"))).isTrue();
        assertThat(scope.canAccess(withDepartment(" engineering "))).isTrue();
        assertThat(scope.canAccess(withDepartment("Infrastructure"))).isFalse();
        assertThat(scope.canAccess(withDepartment(null))).isFalse();
        assertThat(scope.canAccess(withDepartment(""))).isFalse();
        assertThat(scope.canAccess(null)).isFalse();
    }

    @Test
    void userWithNullOrBlankDepartmentsGetsNone() {
        login("testuser", "ROLE_USER");
        userWithDepartments(null);
        assertThat(access.currentScope().canAccess(withDepartment("Finance"))).isFalse();

        userWithDepartments("   ");
        assertThat(access.currentScope().canAccess(withDepartment("Finance"))).isFalse();
    }

    @Test
    void unknownUserGetsNone() {
        login("ghost", "ROLE_USER");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        AccessScope scope = access.currentScope();

        assertThat(scope.isUnrestricted()).isFalse();
        assertThat(scope.departments()).isEmpty();
    }

    @Test
    void canAssignFollowsTheSameRule() {
        login("testuser", "ROLE_USER");
        userWithDepartments("Finance");

        AccessScope scope = access.currentScope();

        assertThat(scope.canAssign("finance")).isTrue();
        assertThat(scope.canAssign("HR")).isFalse();
        assertThat(scope.canAssign(null)).isFalse();
    }
}
