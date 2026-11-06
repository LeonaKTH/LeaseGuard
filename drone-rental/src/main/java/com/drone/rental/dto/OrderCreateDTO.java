package com.drone.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 订单创建DTO
 */
@Data
@Schema(description = "订单创建参数")
public class OrderCreateDTO {

    @NotNull(message = "无人机ID不能为空")
    @Schema(description = "无人机ID", required = true)
    private Long droneId;

    @Schema(description = "空域备案ID")
    private Long airspaceRecordId;

    @NotNull(message = "租赁开始日期不能为空")
    @Schema(description = "租赁开始日期", required = true)
    private LocalDate startDate;

    @NotNull(message = "租赁结束日期不能为空")
    @Schema(description = "租赁结束日期", required = true)
    private LocalDate endDate;

    @Schema(description = "收货地址")
    private String deliveryAddress;

    @Schema(description = "订单备注")
    private String remark;
}
