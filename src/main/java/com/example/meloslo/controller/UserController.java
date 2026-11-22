package com.example.meloslo.controller;

import com.example.meloslo.dto.PasswordChangeRequest;
import com.example.meloslo.dto.UserCreateRequest;
import com.example.meloslo.dto.UserUpdateRequest;
import com.example.meloslo.model.Role;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser() {
        Authentication auth = currentAuthentication();
        if (auth == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<User> user = userRepository.findByUsername(auth.getName());
        return user.map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@Valid @RequestBody PasswordChangeRequest request) {
        Authentication auth = currentAuthentication();
        if (auth == null) {
            return ResponseEntity.status(401).build();
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must differ from the current one");
        }

        // Password-only update: never rewrites role or departments from a stale read.
        userRepository.updatePassword(username, passwordEncoder.encode(request.newPassword()));
        expireSessions(username, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @PostMapping
    public User createUser(@Valid @RequestBody UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw duplicateUsername();
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()),
                request.email(), request.departments(),
                request.role() != null ? request.role() : Role.USER);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw duplicateUsername();
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public User updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        String newPassword = request.password();
        boolean passwordChanged = newPassword != null && !newPassword.isBlank();
        if (passwordChanged && (newPassword.length() < MIN_PASSWORD_LENGTH || newPassword.length() > MAX_PASSWORD_LENGTH)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be " + MIN_PASSWORD_LENGTH + "-" + MAX_PASSWORD_LENGTH + " characters");
        }

        // Lock the admin rows first so concurrent role changes and deletions serialize.
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (userRepository.existsByUsernameAndIdNot(request.username(), id)) {
            throw duplicateUsername();
        }

        String oldUsername = user.getUsername();
        Role oldRole = user.getRole() == Role.ADMIN ? Role.ADMIN : Role.USER;
        Role newRole = request.role() != null ? request.role() : oldRole;
        if (oldRole == Role.ADMIN && newRole != Role.ADMIN && isLastAdmin(admins, user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last administrator cannot be demoted");
        }

        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setDepartments(request.departments());
        user.setRole(newRole);
        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw duplicateUsername();
        }

        boolean roleChanged = newRole != oldRole;
        boolean usernameChanged = !oldUsername.equals(request.username());
        if (roleChanged || passwordChanged || usernameChanged) {
            // A rename must expire every session, including the caller's: their principal still
            // carries the old username. Other edits keep the caller's own session.
            Authentication auth = currentAuthentication();
            boolean self = auth != null && auth.getName().equals(oldUsername);
            String keep = self && !roleChanged && !usernameChanged ? currentSessionId() : null;
            expireSessions(oldUsername, keep);
        }
        return saved;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRole() == Role.ADMIN && isLastAdmin(admins, user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last administrator cannot be deleted");
        }
        userRepository.delete(user);
        expireSessions(user.getUsername(), null);
        return ResponseEntity.ok().build();
    }

    private static boolean isLastAdmin(List<User> admins, User target) {
        return admins.size() <= 1 && admins.stream().anyMatch(a -> a.getId().equals(target.getId()));
    }

    private static ResponseStatusException duplicateUsername() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    }

    private static Authentication currentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth;
    }

    private static String currentSessionId() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getSessionId();
    }

    /** Expires the open sessions of a user so changed rights apply at the next request. */
    private void expireSessions(String username, String keepSessionId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails details && details.getUsername().equals(username)) {
                for (SessionInformation info : sessionRegistry.getAllSessions(principal, false)) {
                    if (!info.getSessionId().equals(keepSessionId)) {
                        info.expireNow();
                    }
                }
            }
        }
    }
}
