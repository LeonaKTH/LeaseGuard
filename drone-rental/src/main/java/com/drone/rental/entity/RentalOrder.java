package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租赁订单实体类
 */
@Data
@TableName("rental_order")
@Schema(name = "RentalOrder", description = "租赁订单实体")
public class RentalOrder implements Serializable {
    private Integer version;
    private Integer inventoryHeld;

    private static final long serialVersionUID = 1L;

    @Schema(description = "订单ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "无人机ID")
    private Long droneId;

    @Schema(description = "空域备案ID")
    private Long airspaceRecordId;

    @Schema(description = "租赁开始时间")
    private LocalDateTime rentalStartTime;

    @Schema(description = "租赁结束时间")
    private LocalDateTime rentalEndTime;

    @Schema(description = "租赁天数")
    private Integer rentalDays;

    @Schema(description = "单价(元/天)")
    private BigDecimal unitPrice;

    @Schema(description = "订单总金额")
    private BigDecimal totalAmount;

    @Schema(description = "押金金额")
    private BigDecimal depositAmount;

    @Schema(description = "订单状态: 0-待结算, 1-待交付, 2-待接收, 3-租赁中, 4-已归还, 5-已取消, 6-已退款")
    private Integer orderStatus;

    @Schema(description = "收货地址")
    private String deliveryAddress;

    @Schema(description = "订单备注")
    private String remark;

    @Schema(description = "取消原因")
    private String cancelReason;

    @Schema(description = "取消时间")
    private LocalDateTime cancelTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @Schema(description = "逻辑删除标志")
    @TableLogic
    private Integer deleted;
}
