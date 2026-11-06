package com.drone.rental.controller;
import com.drone.rental.common.Result;
import com.drone.rental.service.*;
import com.drone.rental.dto.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @Tag(name="派工与任务管理",description="ADMIN专用；任务只读，不提供FAILED重试")
public class G4ManagementController {
 private final WorkbenchService views;private final MaintenanceTicketService repairs;private final AuditViewService audit;
 public G4ManagementController(WorkbenchService v,MaintenanceTicketService r,AuditViewService a){views=v;repairs=r;audit=a;}
 @GetMapping("/admin/technicians") @Operation(summary="启用的真实技师目录") public Result<java.util.List<WorkbenchService.Technician>> technicians(){return Result.success(views.technicians());}
 @PostMapping("/admin/technicians") @Operation(summary="管理员建立BCrypt技师账号，不接受客户端角色") public Result<WorkbenchService.Technician> create(@jakarta.validation.Valid @RequestBody TechnicianCreateDTO dto){return Result.success(views.createTechnician(dto));}
 @PostMapping("/admin/maintenance/ticket/{id}/assignment") @Operation(summary="仅PENDING派工；必须当前version，冲突409") public Result<Void> assign(@PathVariable String id,@jakarta.validation.Valid @RequestBody AssignmentDTO dto){repairs.assign(Ids.parse(id),Ids.parse(dto.assigneeId()),dto.version());return Result.success();}
 @GetMapping("/admin/tasks") @Operation(summary="管理员只读任务分页，排除租约token和内部请求") public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<AuditViewService.Task>> tasks(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)String status){return Result.success(audit.tasks(page,pageSize,status));}
 @GetMapping("/audit/{kind}/{id}") @Operation(summary="授权订单或工单事件，kind=orders|tickets，最多100") public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<AuditViewService.Event>> events(@PathVariable String kind,@PathVariable String id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return Result.success(audit.events(kind,Ids.parse(id),page,pageSize));}
}
