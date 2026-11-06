package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 诚信记录实体
 */
@Data
@TableName("credit_record")
@Schema(description = "诚信记录")
public class CreditRecord {

    @TableId(type = IdType.AUTO)
    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "变更类型: 1-标记不良, 2-恢复正常, 3-系统扣分, 4-系统加分")
    private Integer changeType;

    @Schema(description = "变更前状态: 0-不良, 1-正常")
    private Integer beforeStatus;

    @Schema(description = "变更后状态: 0-不良, 1-正常")
    private Integer afterStatus;

    @Schema(description = "变更原因")
    private String reason;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "操作人ID(管理员)")
    private Long operatorId;

    @Schema(description = "操作人名称")
    private String operatorName;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @Schema(description = "备注")
    private String remark;
}
