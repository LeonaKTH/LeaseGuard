package com.drone.rental.dto;
import jakarta.validation.constraints.Size;
public record AcceptanceDTO(@Size(max=500) String remark) {}
