package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 维修工单实体类
 */
@Data
@TableName("maintenance_ticket")
@Schema(name = "MaintenanceTicket", description = "维修工单实体")
public class MaintenanceTicket implements Serializable {
    private Integer version;
    private Long assigneeId;
    private java.time.LocalDateTime assignedAt;

    private static final long serialVersionUID = 1L;

    @Schema(description = "工单ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "工单编号")
    private String ticketNo;

    @Schema(description = "故障上报ID")
    private Long faultReportId;

    @Schema(description = "无人机ID")
    private Long droneId;

    @Schema(description = "关联用户ID")
    private Long userId;

    @Schema(description = "维修类型")
    private String maintenanceType;

    @Schema(description = "维修描述")
    private String maintenanceDescription;

    @Schema(description = "状态: 0-待维修, 1-维修中, 2-已完成, 4-待验收")
    private Integer status;
    private java.time.LocalDateTime acceptedAt;
    private Long acceptedBy;
    private String acceptanceRemark;

    @Schema(description = "预估费用")
    private BigDecimal estimatedCost;

    @Schema(description = "实际费用")
    private BigDecimal actualCost;

    @Schema(description = "预估维修天数")
    private Integer estimatedDays;

    @Schema(description = "实际维修天数")
    private Integer actualDays;

    @Schema(description = "维修开始时间")
    private LocalDateTime startTime;

    @Schema(description = "维修完成时间")
    private LocalDateTime completeTime;

    @Schema(description = "进度备注(JSON数组)")
    private String progressNotes;

    @Schema(description = "维修负责人")
    private String assigneeName;

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
