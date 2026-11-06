package com.drone.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 维修工单更新DTO
 */
@Data
@Schema(description = "维修工单参数")
public class MaintenanceTicketDTO {

    @Schema(description = "维修类型")
    private String maintenanceType;

    @Schema(description = "维修描述")
    private String maintenanceDescription;

    @Schema(description = "状态: 0-待维修, 1-维修中, 2-已完成, 4-待验收（禁止通用接口修改）")
    private Integer status;

    @Schema(description = "预估费用")
    @jakarta.validation.constraints.DecimalMin("0.00")
    @jakarta.validation.constraints.Digits(integer=8,fraction=2)
    private BigDecimal estimatedCost;

    @Schema(description = "实际费用")
    @jakarta.validation.constraints.DecimalMin("0.00")
    @jakarta.validation.constraints.Digits(integer=8,fraction=2)
    private BigDecimal actualCost;

    @Schema(description = "预估维修天数")
    private Integer estimatedDays;

    @Schema(description = "实际维修天数")
    private Integer actualDays;

    @Schema(description = "维修负责人")
    private String assigneeName;

    @Schema(description = "进度备注")
    private String progressNote;
}
