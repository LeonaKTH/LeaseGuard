package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 空域备案实体类
 */
@Data
@TableName("airspace_record")
@Schema(name = "AirspaceRecord", description = "空域备案实体")
public class AirspaceRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "备案ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "飞行区域名称")
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

    @Schema(description = "审核状态: 0-待审核, 1-审核通过, 2-审核拒绝")
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
