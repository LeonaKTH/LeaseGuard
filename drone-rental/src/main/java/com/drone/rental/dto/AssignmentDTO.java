package com.drone.rental.dto;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record AssignmentDTO(@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") @Schema(type="string",description="技师十进制ID") String assigneeId,@NotNull @PositiveOrZero Integer version) {}
