package com.drone.rental.controller;
import com.drone.rental.common.Result;
import com.drone.rental.service.*;
import com.drone.rental.vo.TicketView;
import com.drone.rental.dto.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @Tag(name="维修工作台",description="ADMIN可读全部，TECHNICIAN只读本人派工；开始/提交仅被分配技师")
@RequestMapping("/workbench/tickets")
public class WorkbenchController {
 private final WorkbenchService views;private final MaintenanceTicketService repairs;
 public WorkbenchController(WorkbenchService v,MaintenanceTicketService r){views=v;repairs=r;}
 @GetMapping @Operation(summary="数据库范围过滤的工作台分页，ID与金额为字符串") public Result<Page<TicketView>> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)Integer status){return Result.success(views.tickets(page,pageSize,status));}
 @GetMapping("/{id}") @Operation(summary="仅管理员或分配技师的工单详情") public Result<TicketView> detail(@PathVariable String id){return Result.success(views.ticket(Ids.parse(id)));}
 @PostMapping("/{id}/start") @Operation(summary="分配技师开始维修，支持Idempotency-Key") public Result<Void> start(@PathVariable String id){repairs.startMaintenance(Ids.parse(id));return Result.success();}
 @PostMapping("/{id}/complete") @Operation(summary="分配技师提交待验收，金额由BigDecimal处理") public Result<Void> complete(@PathVariable String id,@jakarta.validation.Valid @RequestBody MaintenanceTicketDTO dto){repairs.completeMaintenance(Ids.parse(id),dto);return Result.success();}
}
