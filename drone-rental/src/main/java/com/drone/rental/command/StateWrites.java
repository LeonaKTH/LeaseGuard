package com.drone.rental.command;
import com.drone.rental.entity.*;
import com.drone.rental.mapper.*;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.drone.rental.common.exception.BusinessException;
import org.springframework.stereotype.Service;
@Service
public class StateWrites {
 private final RentalOrderMapper orders;private final FaultReportMapper faults;private final MaintenanceTicketMapper tickets;private final EventRecorder events;
 public StateWrites(RentalOrderMapper orders,FaultReportMapper faults,MaintenanceTicketMapper tickets,EventRecorder events){this.orders=orders;this.faults=faults;this.tickets=tickets;this.events=events;}
 private void one(int n){if(n!=1)throw new BusinessException(409,"状态或版本已变化");}
 public void order(RentalOrder x,int from){int v=x.getVersion();x.setVersion(v+1);one(orders.update(x,new LambdaUpdateWrapper<RentalOrder>().eq(RentalOrder::getId,x.getId()).eq(RentalOrder::getOrderStatus,from).eq(RentalOrder::getVersion,v)));events.record("ORDER",x.getId(),CommandContext.required().operation,from,x.getOrderStatus());}
 public void fault(FaultReport x,int from){int v=x.getVersion();x.setVersion(v+1);one(faults.update(x,new LambdaUpdateWrapper<FaultReport>().eq(FaultReport::getId,x.getId()).eq(FaultReport::getAuditStatus,from).eq(FaultReport::getVersion,v)));events.record("FAULT",x.getId(),CommandContext.required().operation,from,x.getAuditStatus());}
 public void ticket(MaintenanceTicket x,int from){int v=x.getVersion();x.setVersion(v+1);one(tickets.update(x,new LambdaUpdateWrapper<MaintenanceTicket>().eq(MaintenanceTicket::getId,x.getId()).eq(MaintenanceTicket::getStatus,from).eq(MaintenanceTicket::getVersion,v)));events.record("TICKET",x.getId(),CommandContext.required().operation,from,x.getStatus());}
}
