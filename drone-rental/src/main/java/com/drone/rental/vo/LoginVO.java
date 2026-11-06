package com.drone.rental.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录返回VO
 */
@Data
@Schema(description = "登录返回信息")
public class LoginVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "角色: 0-普通用户, 1-管理员")
    private Integer role;

    @Schema(description = "Token")
    private String token;
}
