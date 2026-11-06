package com.drone.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 故障上报DTO
 */
@Data
@Schema(description = "故障上报参数")
public class FaultReportDTO {

    @NotNull(message = "无人机ID不能为空")
    @Schema(description = "无人机ID", required = true)
    private Long droneId;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "故障类型")
    private String faultType;

    @NotBlank(message = "故障描述不能为空")
    @Schema(description = "故障描述", required = true)
    private String faultDescription;

    @Schema(description = "故障图片(JSON数组)")
    private String faultImages;

    @Schema(description = "故障发生时间")
    private LocalDateTime faultTime;
}
