package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 故障上报实体类
 */
@Data
@TableName("fault_report")
@Schema(name = "FaultReport", description = "故障上报实体")
public class FaultReport implements Serializable {
    private Integer version;

    private static final long serialVersionUID = 1L;

    @Schema(description = "故障ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "故障单号")
    private String reportNo;

    @Schema(description = "上报用户ID")
    private Long userId;

    @Schema(description = "无人机ID")
    private Long droneId;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "故障类型")
    private String faultType;

    @Schema(description = "故障描述")
    private String faultDescription;

    @Schema(description = "故障图片(JSON数组)")
    private String faultImages;

    @Schema(description = "故障发生时间")
    private LocalDateTime faultTime;

    @Schema(description = "审核状态: 0-待审核, 1-确认故障, 2-非故障")
    private Integer auditStatus;

    @Schema(description = "审核备注")
    private String auditRemark;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "审核人ID")
    private Long auditorId;

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
