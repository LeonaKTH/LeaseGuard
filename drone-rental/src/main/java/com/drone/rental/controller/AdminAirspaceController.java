package com.drone.rental.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.drone.rental.common.Result;
import com.drone.rental.dto.AuditDTO;
import com.drone.rental.entity.AirspaceRecord;
import com.drone.rental.service.AirspaceRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 空域备案管理控制器（管理员端）
 */
@Tag(name = "空域备案-管理员端")
@RestController
@RequestMapping("/admin/airspace")
public class AdminAirspaceController {

    @Autowired
    private AirspaceRecordService airspaceRecordService;

    @Operation(summary = "分页查询空域备案列表")
    @GetMapping("/list")
    public Result<IPage<AirspaceRecord>> pageAirspaceRecords(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "审核状态") @RequestParam(required = false) Integer auditStatus,
            @Parameter(description = "区域名称") @RequestParam(required = false) String regionName) {
        IPage<AirspaceRecord> page = airspaceRecordService.pageAirspaceRecords(pageNum, pageSize, auditStatus, regionName);
        return Result.success(page);
    }

    @Operation(summary = "审核空域备案")
    @PutMapping("/{id}/audit")
    public Result<Void> auditAirspaceRecord(
            @PathVariable Long id,
            @Validated @RequestBody AuditDTO dto) {
        airspaceRecordService.auditAirspaceRecord(id, dto);
        return Result.success();
    }

    @Operation(summary = "获取空域备案详情")
    @GetMapping("/{id}")
    public Result<AirspaceRecord> getAirspaceRecordDetail(@PathVariable Long id) {
        AirspaceRecord record = airspaceRecordService.getById(id);
        return Result.success(record);
    }
}
