package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户飞行资质实体类
 */
@Data
@TableName("user_qualification")
@Schema(name = "UserQualification", description = "用户飞行资质实体")
public class UserQualification implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "资质ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "证件号码")
    private String certificateNo;

    @Schema(description = "证件类型")
    private String certificateType;

    @Schema(description = "证件图片URL")
    private String certificateImage;

    @Schema(description = "有效期开始日期")
    private LocalDate validStartDate;

    @Schema(description = "有效期结束日期")
    private LocalDate validEndDate;

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
