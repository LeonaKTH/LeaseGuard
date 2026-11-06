package com.drone.rental.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.drone.rental.common.Constants;
import com.drone.rental.command.*;
import com.drone.rental.common.ResultCode;
import com.drone.rental.common.exception.BusinessException;
import com.drone.rental.dto.OrderCreateDTO;
import com.drone.rental.entity.Drone;
import com.drone.rental.entity.Payment;
import com.drone.rental.entity.RentalOrder;
import com.drone.rental.entity.AirspaceRecord;
import com.drone.rental.entity.User;
import com.drone.rental.mapper.RentalOrderMapper;
import com.drone.rental.security.UserContext;
import com.drone.rental.security.Access;
import com.drone.rental.service.*;
import com.drone.rental.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 订单服务实现类
 */
@Service
public class OrderServiceImpl extends ServiceImpl<RentalOrderMapper, RentalOrder> implements OrderService {
    @Autowired private ListAssembler listAssembler;
    @Autowired private CommandExecutor commands;
    @Autowired private com.drone.rental.jobs.JobQueue jobQueue;
    @Autowired private com.drone.rental.jobs.SystemActor systemActor;
    @Autowired private java.time.Clock jobClock;
    @Autowired private EventRecorder events;
    @Autowired private StateWrites stateWrites;
    @Autowired private InventoryService inventory;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;


    @Autowired private com.drone.rental.mapper.ReturnRequestMapper returns;
    @Autowired
    private UserService userService;

    @Autowired
    private DroneService droneService;

    @Autowired
    private AirspaceRecordService airspaceRecordService;

    @Autowired
    @Lazy
    private PaymentService paymentService;

    @Autowired
    private UserQualificationService qualificationService;

    @Override
    public OrderVO createOrder(OrderCreateDTO dto) {
        return commands.execute("CREATE_ORDER","DRONE",dto.getDroneId(),java.util.Arrays.asList(dto),()->{ return createOrderLocked(dto); });
    }

    private OrderVO createOrderLocked(OrderCreateDTO dto) {
        Long userId = UserContext.getCurrentUserId();

        // 1. 检查用户状态
        userService.checkUserOperable(userId);

        // 2. 检查用户资质
        qualificationService.checkQualificationValid(userId);

        // 3. 检查无人机是否可租赁
        droneService.checkDroneRentable(dto.getDroneId());

        // 4. 检查空域备案（如果提供了空域备案ID）
        if (dto.getAirspaceRecordId() != null) {
            airspaceRecordService.checkAirspaceRecordValid(dto.getAirspaceRecordId(), userId);
        }

        // 5. 计算租赁天数和金额
        Drone drone = droneService.getById(dto.getDroneId());
        long days = ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;
        if (days <= 0) {
            throw new BusinessException("租赁结束日期必须大于等于开始日期");
        }

        BigDecimal totalAmount = drone.getPricePerDay().multiply(BigDecimal.valueOf(days));

        // 6. 创建订单
        RentalOrder order = new RentalOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setDroneId(dto.getDroneId());
        order.setAirspaceRecordId(dto.getAirspaceRecordId());
        order.setRentalStartTime(dto.getStartDate().atStartOfDay());
        order.setRentalEndTime(dto.getEndDate().atTime(23, 59, 59));
        order.setRentalDays((int) days);
        order.setUnitPrice(drone.getPricePerDay());
        order.setTotalAmount(totalAmount);
        order.setDepositAmount(BigDecimal.ZERO); // 暂不收押金
        order.setOrderStatus(Constants.ORDER_STATUS_UNPAID);
        order.setRemark(dto.getRemark());

        order.setCreatedTime(LocalDateTime.now(jobClock).withNano(0));order.setUpdatedTime(order.getCreatedTime());
        this.save(order);
        events.record("ORDER",order.getId(),"ORDER_CREATED",null,0);

        // 7. 减少库存
        droneService.decreaseStock(dto.getDroneId(), 1, order.getId());

        // 8. 创建支付记录
        paymentService.createPayment(order);
        jobQueue.enqueue(order);

        return convertToVO(order);
    }

    @Override
    public OrderVO getOrderDetail(Long orderId) {
        com.drone.rental.security.Roles.customer();
        RentalOrder order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }

        // 普通用户只能查看自己的订单
        Long currentUserId = UserContext.getCurrentUserId();
        if (!UserContext.isAdmin() && !order.getUserId().equals(currentUserId)) {
            throw new BusinessException(404,"资源不存在");
        }

        return convertToVO(order);
    }

    @Override
    public IPage<OrderVO> getCurrentUserOrders(Integer pageNum, Integer pageSize, Integer orderStatus) {
        com.drone.rental.security.Roles.customer();
        Long userId = UserContext.getCurrentUserId();
        ListAssembler.page(pageNum,pageSize);
        Page<RentalOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(RentalOrder::getUserId, userId);
        if (orderStatus != null) {
            wrapper.eq(RentalOrder::getOrderStatus, orderStatus);
        }
        wrapper.orderByDesc(RentalOrder::getCreatedTime).orderByDesc(RentalOrder::getId);

        IPage<RentalOrder> orderPage = this.page(page, wrapper);
        var result=new Page<OrderVO>(orderPage.getCurrent(),orderPage.getSize(),orderPage.getTotal());result.setRecords(listAssembler.orders(orderPage.getRecords()));return result;
    }

    @Override
    public IPage<OrderVO> pageOrders(Integer pageNum, Integer pageSize, String orderNo,
                                      String userPhone, Integer orderStatus, String startDate, String endDate) {
        Access.admin();
        ListAssembler.page(pageNum,pageSize);
        Page<RentalOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(orderNo)) {
            wrapper.like(RentalOrder::getOrderNo, orderNo);
        }
        if (orderStatus != null) {
            wrapper.eq(RentalOrder::getOrderStatus, orderStatus);
        }
        // 如果有用户手机号筛选，先查询用户ID
        if (StringUtils.hasText(userPhone)) {
            wrapper.apply("EXISTS(SELECT 1 FROM user filter_user WHERE filter_user.id=rental_order.user_id AND filter_user.deleted=0 AND filter_user.phone LIKE {0})","%"+userPhone+"%");
        }
        // 日期范围筛选
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(RentalOrder::getCreatedTime, startDate + " 00:00:00");
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(RentalOrder::getCreatedTime, endDate + " 23:59:59");
        }
        wrapper.orderByDesc(RentalOrder::getCreatedTime).orderByDesc(RentalOrder::getId);

        IPage<RentalOrder> orderPage = this.page(page, wrapper);
        var result=new Page<OrderVO>(orderPage.getCurrent(),orderPage.getSize(),orderPage.getTotal());result.setRecords(listAssembler.orders(orderPage.getRecords()));return result;
    }

    @Override
    public void simulatePay(Long orderId, String deliveryAddress) {
        commands.execute("PAY","ORDER",orderId,java.util.Arrays.asList(orderId,deliveryAddress),()->{ simulatePayLocked(orderId,deliveryAddress); return null; });
    }

    private void simulatePayLocked(Long orderId, String deliveryAddress) {
        RentalOrder order = baseMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }

        // 检查是否是自己的订单
        Long currentUserId = UserContext.getCurrentUserId();
        if (!order.getUserId().equals(currentUserId)) {
            throw new BusinessException(404,"资源不存在");
        }

        if (order.getOrderStatus() != Constants.ORDER_STATUS_UNPAID) {
            throw new BusinessException(ResultCode.ORDER_ALREADY_PAID);
        }

        // 保存收货地址
        if (deliveryAddress != null && !deliveryAddress.trim().isEmpty()) {
            order.setDeliveryAddress(deliveryAddress.trim());
        }

        // 更新订单状态
        order.setOrderStatus(Constants.ORDER_STATUS_PAID);
        stateWrites.order(order,0);

        // 检查支付记录是否存在，不存在则创建
        Payment existingPayment = paymentService.getByOrderId(orderId);
        if (existingPayment == null) {
            // 创建支付记录
            paymentService.createPayment(order);
        }

        // 更新支付状态
        paymentService.updatePaymentStatus(orderId, Constants.PAYMENT_STATUS_PAID);
    }

    @Override
    public void cancelOrder(Long orderId, String reason) {
        commands.execute("CANCEL","ORDER",orderId,java.util.Arrays.asList(orderId,reason),()->{ cancelOrderLocked(orderId,reason); return null; });
    }

    private void cancelOrderLocked(Long orderId, String reason) {
        RentalOrder order = baseMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }

        // 检查是否是自己的订单
        Long currentUserId = UserContext.getCurrentUserId();
        boolean system=CommandContext.required().operation.equals("TIMEOUT_CANCEL") && CommandContext.required().actor==systemActor.id();
        if (!system && !UserContext.isAdmin() && !order.getUserId().equals(currentUserId)) {
            throw new BusinessException(404,"资源不存在");
        }

        // 只能取消待支付的订单
        if (order.getOrderStatus() != Constants.ORDER_STATUS_UNPAID) {
            throw new BusinessException(ResultCode.ORDER_CANNOT_CANCEL);
        }

        order.setOrderStatus(Constants.ORDER_STATUS_CANCELLED);
        order.setCancelReason(reason);
        order.setCancelTime(LocalDateTime.now());
        stateWrites.order(order,0);

        // 恢复库存
        droneService.increaseStock(order.getDroneId(), 1, orderId);
    }

    @Override
    public com.drone.rental.jobs.TimeoutResult timeoutCancel(Long orderId) {
        return commands.executeTimeout(systemActor,orderId,()->{
            var due=jdbc.queryForList("SELECT due_at FROM job_task WHERE order_id=? AND type='ORDER_TIMEOUT'",orderId);
            if(due.isEmpty())throw new BusinessException(409,"历史订单没有自动取消任务");
            Object storedDue=due.getFirst().get("due_at");LocalDateTime deadline=storedDue instanceof LocalDateTime local?local:((java.sql.Timestamp)storedDue).toLocalDateTime();
            if(LocalDateTime.now(jobClock).isBefore(deadline))throw new BusinessException(409,"订单尚未超时");
            var row=jdbc.queryForMap("SELECT order_status,deleted FROM rental_order WHERE id=?",orderId);
            int state=((Number)row.get("order_status")).intValue();
            if(((Number)row.get("deleted")).intValue()!=0 || state!=Constants.ORDER_STATUS_UNPAID){
                events.record("ORDER",orderId,"TIMEOUT_NO_ACTION",state,state);
                return com.drone.rental.jobs.TimeoutResult.NO_ACTION;
            }
            // Existing public cancellation command joins the already locked system command.
            cancelOrder(orderId,"未支付订单超时自动取消");
            return com.drone.rental.jobs.TimeoutResult.CANCELLED;
        });
    }

    @Override
    public void confirmReturn(Long orderId) {
        commands.execute("CONFIRM_RETURN","ORDER",orderId,java.util.Arrays.asList(orderId),()->{ confirmReturnLocked(orderId); return null; });
    }

    private void confirmReturnLocked(Long orderId) {
        Access.admin();
        reviewReturn(orderId, true, "管理员确认归还验收");
    }

    @Override
    public com.drone.rental.entity.ReturnRequest getReturnRequest(Long orderId) {
        getOrderDetail(orderId);
        return findReturn(orderId);
    }

    private com.drone.rental.entity.ReturnRequest findReturn(Long orderId) {
        return returns.selectOne(new LambdaQueryWrapper<com.drone.rental.entity.ReturnRequest>().eq(com.drone.rental.entity.ReturnRequest::getOrderId,orderId));
    }

    @Override
    public void reviewReturn(Long orderId, boolean approved, String remark) {
        commands.execute("REVIEW_RETURN","ORDER",orderId,java.util.Arrays.asList(orderId,approved,remark),()->{ reviewReturnLocked(orderId,approved,remark); return null; });
    }

    private void reviewReturnLocked(Long orderId, boolean approved, String remark) {
        Access.admin();
        RentalOrder order=baseMapper.selectForUpdate(orderId);
        if(order==null) Access.missing();
        var request=findReturn(orderId);
        if(request==null) throw new BusinessException(409,"用户尚未提交归还申请");
        Transitions.require(request.getStatus(),0);
        Transitions.require(order.getOrderStatus(),Constants.ORDER_STATUS_RENTING);
        request.setStatus(approved?1:2);request.setReviewedAt(LocalDateTime.now());
        request.setReviewerId(UserContext.getCurrentUserId());request.setReviewRemark(remark);
        int changed=returns.update(request,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.drone.rental.entity.ReturnRequest>().eq(com.drone.rental.entity.ReturnRequest::getId,request.getId()).eq(com.drone.rental.entity.ReturnRequest::getStatus,0).eq(com.drone.rental.entity.ReturnRequest::getVersion,request.getVersion()));
        if(changed!=1)throw new BusinessException(409,"归还申请状态冲突");
        jdbc.update("UPDATE return_request SET version=version+1 WHERE id=?",request.getId());
        events.record("RETURN",request.getId(),approved?"RETURN_APPROVED":"RETURN_REJECTED",0,request.getStatus());
        if(approved){
            order.setOrderStatus(Constants.ORDER_STATUS_RETURNED);stateWrites.order(order,3);
            droneService.increaseStock(order.getDroneId(),1,orderId);
        }
    }

    @Override
    public void refundOrder(Long orderId, String reason) {
        commands.execute("REFUND","ORDER",orderId,java.util.Arrays.asList(orderId,reason),()->{ refundOrderLocked(orderId,reason); return null; });
    }

    private void refundOrderLocked(Long orderId, String reason) {
        Access.admin();
        RentalOrder order = baseMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }

        // 只有已支付的订单可以退款
        if (order.getOrderStatus() != Constants.ORDER_STATUS_PAID) {
            throw new BusinessException("订单状态不允许退款");
        }

        order.setOrderStatus(Constants.ORDER_STATUS_REFUNDED);
        order.setCancelReason(reason);
        order.setCancelTime(LocalDateTime.now());
        stateWrites.order(order,1);

        // 更新支付状态为已退款
        paymentService.refundPayment(orderId, reason);

        // 恢复库存
        droneService.increaseStock(order.getDroneId(), 1, orderId);
    }

    @Override
    public void shipOrder(Long orderId, String expressCompany, String expressNo) {
        commands.execute("SHIP","ORDER",orderId,java.util.Arrays.asList(orderId,expressCompany,expressNo),()->{ shipOrderLocked(orderId,expressCompany,expressNo); return null; });
    }

    private void shipOrderLocked(Long orderId, String expressCompany, String expressNo) {
        Access.admin();
        RentalOrder order = baseMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }

        // 只有已支付的订单可以发货
        if (order.getOrderStatus() != Constants.ORDER_STATUS_PAID) {
            throw new BusinessException("订单状态不允许发货，只有已支付的订单才能发货");
        }

        // 更新订单状态为待收货
        order.setOrderStatus(Constants.ORDER_STATUS_SHIPPED);
        order.setRemark("快递公司: " + expressCompany + ", 快递单号: " + expressNo);
        stateWrites.order(order,1);
    }

    @Override
    public void confirmReceive(Long orderId) {
        commands.execute("RECEIVE","ORDER",orderId,java.util.Arrays.asList(orderId),()->{ confirmReceiveLocked(orderId); return null; });
    }

    private void confirmReceiveLocked(Long orderId) {
        RentalOrder order = baseMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }

        // 检查是否是自己的订单
        Long currentUserId = UserContext.getCurrentUserId();
        if (!order.getUserId().equals(currentUserId)) {
            throw new BusinessException(404,"资源不存在");
        }

        // 只有待收货的订单可以确认收货
        if (order.getOrderStatus() != Constants.ORDER_STATUS_SHIPPED) {
            throw new BusinessException("订单状态不允许确认收货");
        }

        // 更新订单状态为租赁中
        order.setOrderStatus(Constants.ORDER_STATUS_RENTING);
        stateWrites.order(order,2);
    }

    @Override
    public void applyReturn(Long orderId) {
        commands.execute("APPLY_RETURN","ORDER",orderId,java.util.Arrays.asList(orderId),()->{ applyReturnLocked(orderId); return null; });
    }

    private void applyReturnLocked(Long orderId) {
        RentalOrder order = baseMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }

        // 检查是否是自己的订单
        Long currentUserId = UserContext.getCurrentUserId();
        if (!order.getUserId().equals(currentUserId)) {
            throw new BusinessException(404,"资源不存在");
        }

        // 只有租赁中的订单可以申请退租
        if (order.getOrderStatus() != Constants.ORDER_STATUS_RENTING) {
            throw new BusinessException("订单状态不允许申请退租");
        }

        var existing=findReturn(orderId);
        if(existing!=null){
            if(existing.getStatus()==0)return; // Single-request retry returns existing pending request.
            Transitions.require(existing.getStatus(),2);
            existing.setStatus(0);existing.setSubmittedAt(LocalDateTime.now());
            existing.setReviewedAt(null);existing.setReviewerId(null);existing.setReviewRemark(null);
            if(jdbc.update("UPDATE return_request SET status=0,submitted_at=?,reviewed_at=NULL,reviewer_id=NULL,review_remark=NULL,version=version+1 WHERE id=? AND status=2 AND version=?",existing.getSubmittedAt(),existing.getId(),existing.getVersion())!=1)throw new BusinessException(409,"归还申请状态冲突");
            events.record("RETURN",existing.getId(),"RETURN_REQUESTED",2,0);
        }else{
            var request=new com.drone.rental.entity.ReturnRequest();request.setOrderId(orderId);
            request.setUserId(currentUserId);request.setStatus(0);request.setSubmittedAt(LocalDateTime.now());returns.insert(request);events.record("RETURN",request.getId(),"RETURN_REQUESTED",null,0);
        }
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase();
    }

    /**
     * 转换为VO
     */
    private OrderVO convertToVO(RentalOrder order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setReturnRequest(findReturn(order.getId()));

        // 获取用户信息
        User user = userService.getById(order.getUserId());
        if (user != null) {
            vo.setUsername(user.getNickname() != null ? user.getNickname() : user.getUsername());
        }

        // 获取无人机信息
        Drone drone = droneService.getById(order.getDroneId());
        if (drone != null) {
            vo.setDroneModel(drone.getModel());
            vo.setDroneImage(drone.getImage());
        }

        // 获取空域备案信息
        AirspaceRecord record = airspaceRecordService.getById(order.getAirspaceRecordId());
        if (record != null) {
            vo.setRegionName(record.getRegionName());
        }

        return vo;
    }
}
