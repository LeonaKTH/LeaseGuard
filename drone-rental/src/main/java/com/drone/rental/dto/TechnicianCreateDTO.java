package com.drone.rental.dto;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record TechnicianCreateDTO(@NotBlank @Pattern(regexp="[A-Za-z0-9_]{4,20}") String username,@NotBlank @Size(min=12,max=64) @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String password) {}
