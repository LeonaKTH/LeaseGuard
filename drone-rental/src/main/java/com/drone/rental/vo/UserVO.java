package com.drone.rental.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息VO
 */
@Data
@Schema(description = "用户信息")
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "住址")
    private String address;

    @Schema(description = "角色: 0-普通用户, 1-管理员")
    private Integer role;

    @Schema(description = "状态: 0-禁用, 1-正常")
    private Integer status;

    @Schema(description = "诚信状态: 0-不良, 1-正常")
    private Integer creditStatus;

    @Schema(description = "实名认证状态: 0-未认证, 1-待审核, 2-已认证, 3-未通过")
    private Integer verificationStatus;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "身份证号")
    private String idCard;

    @Schema(description = "订单数量")
    private Integer orderCount;

    @Schema(description = "消费金额")
    private java.math.BigDecimal totalSpent;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
