package com.drone.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 空域备案DTO
 */
@Data
@Schema(description = "空域备案参数")
public class AirspaceRecordDTO {

    @NotBlank(message = "飞行区域名称不能为空")
    @Schema(description = "飞行区域名称", required = true)
    private String regionName;

    @Schema(description = "飞行区域详细地址")
    private String regionAddress;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "飞行半径(米)")
    private Integer radius;

    @Schema(description = "最大飞行高度(米)")
    private Integer maxAltitude;

    @Schema(description = "计划开始时间")
    private LocalDateTime plannedStartTime;

    @Schema(description = "计划结束时间")
    private LocalDateTime plannedEndTime;

    @Schema(description = "飞行用途")
    private String purpose;
}
