package com.drone.rental.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户飞行资质VO
 */
@Data
@Schema(description = "用户飞行资质信息")
public class UserQualificationVO {

    @Schema(description = "资质ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

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

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
