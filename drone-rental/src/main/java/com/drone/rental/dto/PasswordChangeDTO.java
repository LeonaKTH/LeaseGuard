package com.drone.rental.dto;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data
public class PasswordChangeDTO {
 @NotBlank private String oldPassword;
 @NotBlank @Size(min=12,max=64) private String newPassword;
}
