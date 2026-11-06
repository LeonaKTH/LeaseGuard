package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论实体类
 */
@Data
@TableName("comment")
@Schema(name = "Comment", description = "评论实体")
public class Comment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "评论ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "无人机ID")
    private Long droneId;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "评分: 1-5星")
    private Integer rating;

    @Schema(description = "评论图片(JSON数组)")
    private String images;

    @Schema(description = "状态: 0-已屏蔽, 1-正常")
    private Integer status;

    @Schema(description = "管理员回复内容")
    private String replyContent;

    @Schema(description = "回复时间")
    private LocalDateTime replyTime;

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
