package com.drone.rental.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.drone.rental.common.Result;
import com.drone.rental.entity.Comment;
import com.drone.rental.service.CommentService;
import com.drone.rental.vo.CommentVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 评论管理控制器（管理员端）
 */
@Tag(name = "评论管理-管理员端")
@RestController
@RequestMapping("/admin/comment")
public class AdminCommentController {

    @Autowired
    private CommentService commentService;

    @Operation(summary = "分页查询评论列表")
    @GetMapping("/list")
    public Result<IPage<CommentVO>> pageComments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "无人机ID") @RequestParam(required = false) Long droneId,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "用户昵称") @RequestParam(required = false) String userName,
            @Parameter(description = "设备型号") @RequestParam(required = false) String droneModel,
            @Parameter(description = "评分") @RequestParam(required = false) Integer rating,
            @Parameter(description = "是否已回复") @RequestParam(required = false) Boolean hasReply) {
        IPage<CommentVO> result = commentService.pageCommentsVO(page, pageSize, droneId, status, userName, droneModel, rating, hasReply);
        return Result.success(result);
    }

    @Operation(summary = "屏蔽/恢复评论")
    @PutMapping("/{id}/status")
    public Result<Void> updateCommentStatus(
            @PathVariable Long id,
            @Parameter(description = "状态: 0-屏蔽, 1-正常") @RequestParam Integer status) {
        commentService.updateCommentStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "回复评论")
    @PostMapping("/{id}/reply")
    public Result<Void> replyComment(
            @PathVariable Long id,
            @Parameter(description = "回复内容") @RequestBody java.util.Map<String, String> body) {
        String replyContent = body.get("replyContent");
        if (replyContent == null || replyContent.trim().isEmpty()) {
            throw new com.drone.rental.common.exception.BusinessException(com.drone.rental.common.ResultCode.PARAM_MISSING, "replyContent");
        }
        commentService.replyComment(id, replyContent);
        return Result.success();
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        commentService.removeById(id);
        return Result.success();
    }
}
