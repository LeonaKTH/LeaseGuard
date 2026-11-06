package com.drone.rental.dto;
import jakarta.validation.constraints.*;
public record ReturnReviewDTO(@NotNull Boolean approved,@Size(max=500) String remark) {}
