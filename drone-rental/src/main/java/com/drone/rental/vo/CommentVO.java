package com.drone.rental.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论VO（带关联信息）
 */
@Data
@Schema(name = "CommentVO", description = "评论视图对象")
public class CommentVO {

    @Schema(description = "评论ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户昵称")
    private String userNickname;

    @Schema(description = "用户头像")
    private String userAvatar;

    @Schema(description = "无人机ID")
    private Long droneId;

    @Schema(description = "无人机型号")
    private String droneModel;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "评分")
    private Integer rating;

    @Schema(description = "图片")
    private String images;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "回复内容")
    private String reply;

    @Schema(description = "回复时间")
    private LocalDateTime replyTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
