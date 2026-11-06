package com.drone.rental.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 故障报修VO（带关联信息）
 */
@Data
@Schema(name = "FaultReportVO", description = "故障报修视图对象")
public class FaultReportVO {

    @Schema(description = "故障ID")
    private Long id;

    @Schema(description = "故障单号")
    private String reportNo;

    @Schema(description = "上报用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "无人机ID")
    private Long droneId;

    @Schema(description = "无人机型号")
    private String droneModel;

    @Schema(description = "无人机品牌")
    private String droneBrand;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "故障类型")
    private String faultType;

    @Schema(description = "故障描述")
    private String faultDescription;

    @Schema(description = "故障图片")
    private String faultImages;

    @Schema(description = "故障发生时间")
    private LocalDateTime faultTime;

    @Schema(description = "审核状态: 0-待审核, 1-确认故障, 2-非故障")
    private Integer auditStatus;

    @Schema(description = "审核备注")
    private String auditRemark;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "联系电话")
    private String contactPhone;
}
