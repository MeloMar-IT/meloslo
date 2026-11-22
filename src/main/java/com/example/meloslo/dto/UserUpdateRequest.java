package com.example.meloslo.dto;

import com.example.meloslo.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Blank or missing password keeps the current one; a missing role keeps the current role. */
public record UserUpdateRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Email String email,
        @Size(max = 255) String departments,
        Role role,
        @Size(max = 128) String password) {
}
