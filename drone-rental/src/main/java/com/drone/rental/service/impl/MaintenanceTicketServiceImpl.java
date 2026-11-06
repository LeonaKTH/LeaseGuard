package com.drone.rental.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.drone.rental.common.Constants;
import com.drone.rental.command.*;
import com.drone.rental.security.Access;
import com.drone.rental.security.Roles;
import com.drone.rental.security.UserContext;
import com.drone.rental.common.ResultCode;
import com.drone.rental.common.exception.BusinessException;
import com.drone.rental.dto.MaintenanceTicketDTO;
import com.drone.rental.entity.Drone;
import com.drone.rental.entity.FaultReport;
import com.drone.rental.entity.MaintenanceTicket;
import com.drone.rental.entity.User;
import com.drone.rental.mapper.FaultReportMapper;
import com.drone.rental.mapper.MaintenanceTicketMapper;
import com.drone.rental.service.DroneService;
import com.drone.rental.service.Transitions;
import com.drone.rental.service.MaintenanceTicketService;
import com.drone.rental.service.UserService;
import com.drone.rental.vo.MaintenanceTicketVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 维修工单服务实现类
 */
@Service
public class MaintenanceTicketServiceImpl extends ServiceImpl<MaintenanceTicketMapper, MaintenanceTicket>
        implements MaintenanceTicketService {
    @Autowired private com.drone.rental.service.ListAssembler listAssembler;
    @Autowired private CommandExecutor commands;
    @Autowired private EventRecorder events;
    @Autowired private StateWrites stateWrites;
    @Autowired private InventoryService inventory;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;


    @Autowired
    private DroneService droneService;

    @Autowired
    private UserService userService;

    @Autowired
    private FaultReportMapper faultReportMapper;

    @Override
    public void createFromFaultReport(FaultReport faultReport) {
        Access.admin();
        if(faultReport==null || faultReport.getId()==null || faultReportMapper.selectById(faultReport.getId())==null) throw new BusinessException(404,"故障不存在");
        commands.execute("CREATE_TICKET","FAULT",faultReport.getId(),java.util.Arrays.asList(faultReport),()->{ createFromFaultReportLocked(faultReport); return null; });
    }

    private void createFromFaultReportLocked(FaultReport faultReport) {
        Access.admin();
        if(faultReport.getId()==null || faultReportMapper.selectById(faultReport.getId())==null || droneService.getById(faultReport.getDroneId())==null) throw new BusinessException(404,"故障或设备不存在");
        MaintenanceTicket ticket = new MaintenanceTicket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setFaultReportId(faultReport.getId());
        ticket.setDroneId(faultReport.getDroneId());
        ticket.setUserId(faultReport.getUserId());
        ticket.setMaintenanceDescription(faultReport.getFaultDescription());
        ticket.setStatus(Constants.MAINTENANCE_STATUS_PENDING);

        this.save(ticket);
        events.record("TICKET",ticket.getId(),"TICKET_CREATED",null,0);

        // 将无人机状态设为维护中
        droneService.updateStatus(faultReport.getDroneId(), Constants.DRONE_STATUS_MAINTENANCE);
    }

    @Override
    public IPage<MaintenanceTicket> pageMaintenanceTickets(Integer pageNum, Integer pageSize,
                                                            Integer status, Long droneId, String droneModel, String type) {
        Access.admin();
        com.drone.rental.service.ListAssembler.page(pageNum,pageSize);
        Page<MaintenanceTicket> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<MaintenanceTicket> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(MaintenanceTicket::getStatus, status);
        }
        if (droneId != null) {
            wrapper.eq(MaintenanceTicket::getDroneId, droneId);
        }
        // Filter before pagination without loading an unbounded related-ID list
        if (StringUtils.hasText(droneModel)) {
            wrapper.apply("EXISTS(SELECT 1 FROM drone filter_drone WHERE filter_drone.id=maintenance_ticket.drone_id AND filter_drone.deleted=0 AND filter_drone.model LIKE {0})","%"+droneModel+"%");
        }
        // 根据维修类型查询
        if (StringUtils.hasText(type)) {
            wrapper.eq(MaintenanceTicket::getMaintenanceType, type);
        }
        wrapper.orderByDesc(MaintenanceTicket::getCreatedTime).orderByDesc(MaintenanceTicket::getId);

        return this.page(page, wrapper);
    }

    @Override
    public IPage<MaintenanceTicketVO> pageMaintenanceTicketsVO(Integer pageNum, Integer pageSize,
                                                                Integer status, Long droneId, String droneModel, String type) {
        // 先获取原始分页数据
        IPage<MaintenanceTicket> ticketPage = pageMaintenanceTickets(pageNum, pageSize, status, droneId, droneModel, type);

        // 转换为VO
        Page<MaintenanceTicketVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setTotal(ticketPage.getTotal());
        voPage.setRecords(listAssembler.tickets(ticketPage.getRecords()));

        return voPage;
    }

    @Override
    public MaintenanceTicketVO getTicketDetailVO(Long id) {
        MaintenanceTicket ticket = getTicketDetail(id);
        return convertToVO(ticket);
    }

    /**
     * 将实体转换为VO
     */
    private MaintenanceTicketVO convertToVO(MaintenanceTicket ticket) {
        MaintenanceTicketVO vo = new MaintenanceTicketVO();
        BeanUtils.copyProperties(ticket, vo);

        // 获取无人机信息
        if (ticket.getDroneId() != null) {
            Drone drone = droneService.getById(ticket.getDroneId());
            if (drone != null) {
                vo.setDroneModel(drone.getModel());
                vo.setDroneBrand(drone.getBrand());
            }
        }

        // 获取用户信息
        if (ticket.getUserId() != null) {
            User user = userService.getById(ticket.getUserId());
            if (user != null) {
                vo.setUserName(user.getUsername());
            }
        }

        // 获取故障类型（从关联的故障报修）
        if (ticket.getFaultReportId() != null) {
            FaultReport faultReport = faultReportMapper.selectById(ticket.getFaultReportId());
            if (faultReport != null) {
                vo.setFaultType(faultReport.getFaultType());
            }
        }

        return vo;
    }

    @Override
    public void updateMaintenanceTicket(Long id, MaintenanceTicketDTO dto) {
        commands.execute("EDIT_TICKET","TICKET",id,java.util.Arrays.asList(id,dto),()->{ updateMaintenanceTicketLocked(id,dto); return null; });
    }

    private void updateMaintenanceTicketLocked(Long id, MaintenanceTicketDTO dto) {
        Access.admin();
        MaintenanceTicket ticket = baseMapper.selectForUpdate(id);
        if (ticket == null) {
            throw new BusinessException(ResultCode.MAINTENANCE_NOT_EXIST);
        }

        if (StringUtils.hasText(dto.getMaintenanceType())) {
            ticket.setMaintenanceType(dto.getMaintenanceType());
        }
        if (StringUtils.hasText(dto.getMaintenanceDescription())) {
            ticket.setMaintenanceDescription(dto.getMaintenanceDescription());
        }
        if (dto.getStatus() != null) throw new BusinessException(409,"状态必须使用专用业务入口");
        if (dto.getEstimatedCost() != null) {
            ticket.setEstimatedCost(dto.getEstimatedCost());
        }
        if (dto.getActualCost() != null) {
            ticket.setActualCost(dto.getActualCost());
        }
        if (dto.getEstimatedDays() != null) {
            ticket.setEstimatedDays(dto.getEstimatedDays());
        }
        if (dto.getActualDays() != null) {
            ticket.setActualDays(dto.getActualDays());
        }
        if(dto.getAssigneeName()!=null)throw new BusinessException(409,"负责人必须通过派工入口设置");

        // 添加进度备注
        if (StringUtils.hasText(dto.getProgressNote())) {
            String currentNotes = ticket.getProgressNotes();
            String newNote = LocalDateTime.now() + ": " + dto.getProgressNote();
            if (StringUtils.hasText(currentNotes)) {
                ticket.setProgressNotes(currentNotes + "\n" + newNote);
            } else {
                ticket.setProgressNotes(newNote);
            }
        }

        stateWrites.ticket(ticket,ticket.getStatus());
    }

    @Override
    public MaintenanceTicket getTicketDetail(Long id) {
        MaintenanceTicket ticket = this.getById(id);
        if (ticket == null) {
            throw new BusinessException(ResultCode.MAINTENANCE_NOT_EXIST);
        }
        if(Integer.valueOf(2).equals(UserContext.getCurrentRole())){
            if(!java.util.Objects.equals(ticket.getAssigneeId(),UserContext.getCurrentUserId()))Access.missing();
        }else Access.visible(ticket.getUserId());
        return ticket;
    }

    @Override
    public MaintenanceTicket getByFaultReportId(Long faultReportId) {
        FaultReport f=faultReportMapper.selectById(faultReportId);
        if(f==null) Access.missing();
        Roles.customer();
        Access.visible(f.getUserId());
        return this.getOne(new LambdaQueryWrapper<MaintenanceTicket>()
                .eq(MaintenanceTicket::getFaultReportId, faultReportId)
                .last("LIMIT 1"));
    }

    @Override
    public void assign(Long id,Long technicianId,Integer expectedVersion){
        commands.execute("ASSIGN_REPAIR","TICKET",id,java.util.Arrays.asList(id,technicianId,expectedVersion),()->{
            Access.admin();var ticket=baseMapper.selectForUpdate(id);if(ticket==null)Access.missing();
            Transitions.require(ticket.getStatus(),Constants.MAINTENANCE_STATUS_PENDING);
            if(expectedVersion==null||expectedVersion<0)throw new BusinessException(400,"派工必须提供版本");
            if(!expectedVersion.equals(ticket.getVersion()))throw new BusinessException(409,"工单已变化，请刷新后派工");
            var rows=jdbc.queryForList("SELECT id,username,role,status,principal_kind FROM user WHERE id=? AND deleted=0 FOR SHARE",technicianId);
            if(rows.isEmpty())throw new BusinessException(400,"技师不存在");var tech=rows.getFirst();
            if(!Integer.valueOf(2).equals(((Number)tech.get("role")).intValue()) || ((Number)tech.get("status")).intValue()!=1 || !"USER".equals(tech.get("principal_kind")))throw new BusinessException(400,"目标必须是启用的真实技师");
            Long previous=ticket.getAssigneeId();ticket.setAssigneeId(technicianId);ticket.setAssigneeName(tech.get("username").toString());ticket.setAssignedAt(LocalDateTime.now());
            stateWrites.ticket(ticket,0);
            var c=CommandContext.required();if(jdbc.update("UPDATE business_event SET details=JSON_OBJECT('fromAssigneeId',?,'toAssigneeId',?,'version',?) WHERE command_id=? AND seq_no=? AND event_type='ASSIGN_REPAIR'",previous==null?null:previous.toString(),technicianId.toString(),ticket.getVersion().toString(),c.id,c.sequence)!=1)throw new IllegalStateException("Missing assignment audit");
            return null;
        });
    }

    @Override
    public void startMaintenance(Long id) {
        commands.execute("START_REPAIR","TICKET",id,java.util.Arrays.asList(id),()->{ startMaintenanceLocked(id); return null; });
    }

    private void startMaintenanceLocked(Long id) {
        Roles.technician();
        MaintenanceTicket ticket = baseMapper.selectForUpdate(id);
        if (ticket == null) {
            throw new BusinessException(ResultCode.MAINTENANCE_NOT_EXIST);
        }

        if(!java.util.Objects.equals(ticket.getAssigneeId(),UserContext.getCurrentUserId()))Access.missing();

        if (ticket.getStatus() != Constants.MAINTENANCE_STATUS_PENDING) {
            throw new BusinessException("只能开始待维修的工单");
        }

        ticket.setStatus(Constants.MAINTENANCE_STATUS_IN_PROGRESS);
        ticket.setStartTime(LocalDateTime.now());

        // 添加进度记录
        String newNote = LocalDateTime.now() + ": 开始维修";
        ticket.setProgressNotes(newNote);

        stateWrites.ticket(ticket,0);
    }

    @Override
    public void completeMaintenance(Long id, MaintenanceTicketDTO dto) {
        commands.execute("SUBMIT_REPAIR","TICKET",id,java.util.Arrays.asList(id,dto),()->{ completeMaintenanceLocked(id,dto); return null; });
    }

    private void completeMaintenanceLocked(Long id, MaintenanceTicketDTO dto) {
        Roles.technician();
        MaintenanceTicket ticket = baseMapper.selectForUpdate(id);
        if (ticket == null) {
            throw new BusinessException(ResultCode.MAINTENANCE_NOT_EXIST);
        }

        if(!java.util.Objects.equals(ticket.getAssigneeId(),UserContext.getCurrentUserId()))Access.missing();

        Transitions.require(ticket.getStatus(),Constants.MAINTENANCE_STATUS_IN_PROGRESS);

        ticket.setStatus(Constants.MAINTENANCE_STATUS_WAIT_ACCEPTANCE);
        ticket.setCompleteTime(LocalDateTime.now());

        // 计算实际维修天数
        if (ticket.getStartTime() != null) {
            long days = ChronoUnit.DAYS.between(ticket.getStartTime().toLocalDate(),
                    LocalDateTime.now().toLocalDate()) + 1;
            ticket.setActualDays((int) days);
        }

        if (dto != null) {
            if (dto.getActualCost() != null) {
                if(dto.getActualCost().signum()<0||dto.getActualCost().scale()>2||dto.getActualCost().precision()-dto.getActualCost().scale()>8)throw new BusinessException(400,"费用精度不符合要求");
                ticket.setActualCost(dto.getActualCost());
            }
        }

        // 添加进度记录
        String currentNotes = ticket.getProgressNotes();
        String newNote = LocalDateTime.now() + ": 维修完成";
        if (StringUtils.hasText(currentNotes)) {
            ticket.setProgressNotes(currentNotes + "\n" + newNote);
        } else {
            ticket.setProgressNotes(newNote);
        }

        stateWrites.ticket(ticket,1);

        // Completion is a submission for acceptance; device stays unavailable.
    }

    /**
     * 生成工单号
     */
    private String generateTicketNo() {
        return "MNT" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase();
    }
    @Override
    public void acceptMaintenance(Long id,String remark) {
        commands.execute("ACCEPT_REPAIR","TICKET",id,java.util.Arrays.asList(id,remark),()->{ acceptMaintenanceLocked(id,remark); return null; });
    }

    private void acceptMaintenanceLocked(Long id,String remark) {
        Access.admin();MaintenanceTicket ticket=baseMapper.selectForUpdate(id);
        Transitions.require(ticket.getStatus(),Constants.MAINTENANCE_STATUS_WAIT_ACCEPTANCE);
        ticket.setStatus(Constants.MAINTENANCE_STATUS_COMPLETED);
        ticket.setAcceptedAt(LocalDateTime.now());ticket.setAcceptedBy(com.drone.rental.security.UserContext.getCurrentUserId());ticket.setAcceptanceRemark(remark);
        stateWrites.ticket(ticket,4);
        Long active=this.count(new LambdaQueryWrapper<MaintenanceTicket>().eq(MaintenanceTicket::getDroneId,ticket.getDroneId()).in(MaintenanceTicket::getStatus,0,1,4));
        if(active==0){Drone d=droneService.getById(ticket.getDroneId());droneService.updateStatus(d.getId(),d.getStock()>0?Constants.DRONE_STATUS_AVAILABLE:Constants.DRONE_STATUS_OUT_OF_STOCK);}
    }
}
