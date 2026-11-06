package com.drone.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 审核DTO（通用）
 */
@Data
@Schema(description = "审核参数")
public class AuditDTO {

    @NotNull(message = "审核状态不能为空")
    @Schema(description = "审核状态: 1-通过, 2-拒绝", required = true)
    private Integer auditStatus;

    @Schema(description = "审核备注")
    private String auditRemark;
}
