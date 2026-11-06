package com.drone.rental.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.drone.rental.common.Result;
import com.drone.rental.dto.CommentDTO;
import com.drone.rental.entity.Comment;
import com.drone.rental.service.CommentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器（用户端）
 */
@Tag(name = "评论管理-用户端")
@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Operation(summary = "发表评论")
    @PostMapping("/add")
    public Result<Void> addComment(@Validated @RequestBody CommentDTO dto) {
        commentService.addComment(dto);
        return Result.success();
    }

    @Operation(summary = "获取当前用户的评论列表")
    @GetMapping("/my")
    public Result<IPage<Comment>> getCurrentUserComments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Comment> page = commentService.getCurrentUserComments(pageNum, pageSize);
        return Result.success(page);
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        commentService.deleteComment(id);
        return Result.success();
    }
}
