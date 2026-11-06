package com.drone.rental.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.drone.rental.common.Result;
import com.drone.rental.entity.Comment;
import com.drone.rental.entity.Drone;
import com.drone.rental.service.CommentService;
import com.drone.rental.service.DroneService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 无人机控制器（用户端）
 */
@Tag(name = "无人机管理-用户端")
@RestController
@RequestMapping("/drone")
public class DroneController {

    @Autowired
    private DroneService droneService;

    @Autowired
    private CommentService commentService;

    @Operation(summary = "分页查询可租赁无人机列表")
    @GetMapping("/list")
    public Result<IPage<Drone>> listAvailableDrones(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "品牌") @RequestParam(required = false) String brand,
            @Parameter(description = "类型") @RequestParam(required = false) String type,
            @Parameter(description = "状态:1-可租赁,0-缺货") @RequestParam(required = false) Integer status,
            @Parameter(description = "最低价格") @RequestParam(required = false) java.math.BigDecimal minPrice,
            @Parameter(description = "最高价格") @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createdTime") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "desc") String sortOrder) {
        IPage<Drone> result = droneService.pageAvailableDrones(page, pageSize, keyword, brand, type, status, minPrice, maxPrice, sortBy, sortOrder);
        return Result.success(result);
    }

    @Operation(summary = "获取无人机详情")
    @GetMapping("/detail/{id}")
    public Result<Drone> getDroneDetail(@PathVariable Long id) {
        Drone drone = droneService.getDroneDetail(id);
        return Result.success(drone);
    }

    @Operation(summary = "获取无人机品牌列表")
    @GetMapping("/brands")
    public Result<java.util.List<String>> getDroneBrands() {
        java.util.List<String> brands = droneService.getAllBrands();
        return Result.success(brands);
    }

    @Operation(summary = "获取无人机类型列表")
    @GetMapping("/types")
    public Result<java.util.List<String>> getDroneTypes() {
        java.util.List<String> types = droneService.getAllTypes();
        return Result.success(types);
    }

    @Operation(summary = "获取无人机评论列表")
    @GetMapping("/{droneId}/comments")
    public Result<IPage<Comment>> getDroneComments(
            @PathVariable Long droneId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Comment> page = commentService.getDroneComments(droneId, pageNum, pageSize);
        return Result.success(page);
    }
}
