package com.example.meloslo.dto;

import com.example.meloslo.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Email String email,
        @Size(max = 255) String departments,
        Role role) {
}
