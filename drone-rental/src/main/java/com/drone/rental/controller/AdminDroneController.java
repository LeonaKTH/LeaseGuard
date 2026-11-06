package com.drone.rental.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.drone.rental.common.Result;
import com.drone.rental.dto.DroneDTO;
import com.drone.rental.entity.Drone;
import com.drone.rental.service.DroneService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 无人机管理控制器（管理员端）
 */
@Tag(name = "无人机管理-管理员端")
@RestController
@RequestMapping("/admin/drone")
public class AdminDroneController {

    @Autowired
    private DroneService droneService;

    @Operation(summary = "分页查询无人机列表")
    @GetMapping("/list")
    public Result<IPage<Drone>> pageAllDrones(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "关键词(型号/品牌)") @RequestParam(required = false) String keyword,
            @Parameter(description = "类型") @RequestParam(required = false) String type,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "上架状态") @RequestParam(required = false) Integer onShelf) {
        IPage<Drone> result = droneService.pageAllDrones(page, pageSize, keyword, type, status, onShelf);
        return Result.success(result);
    }

    @Operation(summary = "获取无人机详情")
    @GetMapping("/{id}")
    public Result<Drone> getDroneDetail(@PathVariable Long id) {
        Drone drone = droneService.getDroneDetail(id);
        return Result.success(drone);
    }

    @Operation(summary = "添加无人机")
    @PostMapping("/add")
    public Result<Void> addDrone(@Validated @RequestBody DroneDTO dto) {
        droneService.addDrone(dto);
        return Result.success();
    }

    @Operation(summary = "更新无人机")
    @PutMapping("/{id}")
    public Result<Void> updateDrone(@PathVariable Long id, @Validated @RequestBody DroneDTO dto) {
        droneService.updateDrone(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除无人机")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDrone(@PathVariable Long id) {
        droneService.deleteDrone(id);
        return Result.success();
    }

    @Operation(summary = "上架/下架无人机")
    @PutMapping("/{id}/shelf")
    public Result<Void> updateOnShelf(
            @PathVariable Long id,
            @Parameter(description = "上架状态: 0-下架, 1-上架") @RequestParam Integer onShelf) {
        droneService.updateOnShelf(id, onShelf);
        return Result.success();
    }

    @Operation(summary = "更新无人机状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @Parameter(description = "状态: 0-缺货, 1-在售, 2-维护中") @RequestParam Integer status) {
        droneService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "更新无人机库存")
    @PutMapping("/{id}/stock")
    public Result<Void> updateStock(
            @PathVariable Long id,
            @Parameter(description = "新库存数量") @RequestBody java.util.Map<String, Integer> body) {
        Integer stock = body.get("stock");
        droneService.updateStock(id, stock);
        return Result.success();
    }
}
