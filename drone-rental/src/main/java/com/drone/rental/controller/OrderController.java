package com.drone.rental.controller;

import com.drone.rental.common.Result;
import com.drone.rental.dto.OrderCreateDTO;
import com.drone.rental.service.OrderService;
import com.drone.rental.vo.OrderVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器（用户端）
 */
@Tag(name = "订单管理-用户端")
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Operation(summary = "创建订单")
    @PostMapping("/create")
    public Result<OrderVO> createOrder(@Validated @RequestBody OrderCreateDTO dto) {
        OrderVO vo = orderService.createOrder(dto);
        return Result.success(vo);
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/{orderId}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long orderId) {
        OrderVO vo = orderService.getOrderDetail(orderId);
        return Result.success(vo);
    }

    @Operation(summary = "模拟支付")
    @PostMapping("/{orderId}/pay")
    public Result<Void> simulatePay(@PathVariable Long orderId, @RequestBody(required = false) java.util.Map<String, String> params) {
        String deliveryAddress = params != null ? params.get("deliveryAddress") : null;
        orderService.simulatePay(orderId, deliveryAddress);
        return Result.success();
    }

    @Operation(summary = "取消订单")
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancelOrder(
            @PathVariable Long orderId,
            @Parameter(description = "取消原因") @RequestParam(required = false) String reason, @RequestBody(required=false) java.util.Map<String,String> body) {
        orderService.cancelOrder(orderId, reason!=null?reason:body==null?null:body.get("reason"));
        return Result.success();
    }

    @Operation(summary = "确认收货")
    @PostMapping("/{orderId}/receive")
    public Result<Void> confirmReceive(@PathVariable Long orderId) {
        orderService.confirmReceive(orderId);
        return Result.success();
    }

    @Operation(summary = "申请退租")
    @PostMapping("/{orderId}/return")
    public Result<Void> applyReturn(@PathVariable Long orderId) {
        orderService.applyReturn(orderId);
        return Result.success();
    }
    @GetMapping("/{orderId}/return")
    public Result<com.drone.rental.entity.ReturnRequest> returnStatus(@PathVariable Long orderId){return Result.success(orderService.getReturnRequest(orderId));}
}
