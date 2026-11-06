package com.drone.rental.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.drone.rental.common.Result;
import com.drone.rental.dto.QualificationDTO;
import com.drone.rental.dto.PasswordChangeDTO;
import com.drone.rental.dto.UserUpdateDTO;
import com.drone.rental.entity.UserQualification;
import com.drone.rental.service.UserQualificationService;
import com.drone.rental.service.UserService;
import com.drone.rental.vo.OrderVO;
import com.drone.rental.vo.UserVO;
import com.drone.rental.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器（用户端）
 */
@Tag(name = "用户管理-用户端")
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserQualificationService qualificationService;

    @Autowired
    private OrderService orderService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public Result<UserVO> getCurrentUserInfo() {
        UserVO vo = userService.getCurrentUserInfo();
        return Result.success(vo);
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/info")
    public Result<Void> updateUserInfo(@Validated @RequestBody UserUpdateDTO dto) {
        userService.updateUserInfo(dto);
        return Result.success();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> updatePassword(
            @Validated @RequestBody PasswordChangeDTO dto) {
        userService.updatePassword(dto.getOldPassword(), dto.getNewPassword());
        return Result.success();
    }

    @Operation(summary = "提交飞行资质")
    @PostMapping("/qualification")
    public Result<Void> submitQualification(@Validated @RequestBody QualificationDTO dto) {
        qualificationService.submitQualification(dto);
        return Result.success();
    }

    @Operation(summary = "获取当前用户的飞行资质")
    @GetMapping("/qualification")
    public Result<UserQualification> getCurrentUserQualification() {
        UserQualification qualification = qualificationService.getCurrentUserQualification();
        return Result.success(qualification);
    }

    @Operation(summary = "获取当前用户订单列表")
    @GetMapping("/orders")
    public Result<IPage<OrderVO>> getCurrentUserOrders(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "订单状态") @RequestParam(required = false) Integer orderStatus) {
        IPage<OrderVO> page = orderService.getCurrentUserOrders(pageNum, pageSize, orderStatus);
        return Result.success(page);
    }
}
