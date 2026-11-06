package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体类
 */
@Data
@TableName("payment")
@Schema(name = "Payment", description = "支付记录实体")
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "支付ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "支付单号")
    private String paymentNo;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "支付金额")
    private BigDecimal amount;

    @Schema(description = "支付类型: 1-订单支付, 2-押金支付, 3-维修费支付")
    private Integer paymentType;

    @Schema(description = "支付方式")
    private String paymentMethod;

    @Schema(description = "支付状态: 0-未支付, 1-已支付, 2-已退款")
    private Integer paymentStatus;

    @Schema(description = "支付时间")
    private LocalDateTime paymentTime;

    @Schema(description = "退款时间")
    private LocalDateTime refundTime;

    @Schema(description = "退款金额")
    private BigDecimal refundAmount;

    @Schema(description = "退款原因")
    private String refundReason;

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
