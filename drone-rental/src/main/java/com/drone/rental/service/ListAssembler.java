package com.drone.rental.service;
import com.drone.rental.entity.*;
import com.drone.rental.mapper.*;
import com.drone.rental.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/** Only assembles one already paginated page. Related reads are narrow and bounded by page IDs. */
@Component
public class ListAssembler {
 private final UserMapper users;private final DroneMapper drones;private final AirspaceRecordMapper airspaces;private final ReturnRequestMapper returns;private final FaultReportMapper faults;
 public ListAssembler(UserMapper u,DroneMapper d,AirspaceRecordMapper a,ReturnRequestMapper r,FaultReportMapper f){users=u;drones=d;airspaces=a;returns=r;faults=f;}
 private static <E> Set<Long> ids(List<E> rows,Function<E,Long> f){return rows.stream().map(f).filter(Objects::nonNull).collect(Collectors.toSet());}
 private static <E> Map<Long,E> index(List<E> rows,Function<E,Long> f){return rows.stream().collect(Collectors.toMap(f,Function.identity()));}
 private Map<Long,User> users(Set<Long> ids){return ids.isEmpty()?Map.of():index(users.selectList(new LambdaQueryWrapper<User>().select(User::getId,User::getUsername,User::getNickname).in(User::getId,ids)),User::getId);}
 private Map<Long,Drone> drones(Set<Long> ids){return ids.isEmpty()?Map.of():index(drones.selectList(new LambdaQueryWrapper<Drone>().select(Drone::getId,Drone::getModel,Drone::getImage,Drone::getBrand).in(Drone::getId,ids)),Drone::getId);}
 public List<OrderVO> orders(List<RentalOrder> page){
  if(page.isEmpty())return List.of();var us=users(ids(page,RentalOrder::getUserId));var ds=drones(ids(page,RentalOrder::getDroneId));var as=ids(page,RentalOrder::getAirspaceRecordId);
  var regions=as.isEmpty()?Map.<Long,AirspaceRecord>of():index(airspaces.selectList(new LambdaQueryWrapper<AirspaceRecord>().select(AirspaceRecord::getId,AirspaceRecord::getRegionName).in(AirspaceRecord::getId,as)),AirspaceRecord::getId);
  var rs=index(returns.selectList(new LambdaQueryWrapper<ReturnRequest>().in(ReturnRequest::getOrderId,ids(page,RentalOrder::getId))),ReturnRequest::getOrderId);
  return page.stream().map(o->{var vo=new OrderVO();BeanUtils.copyProperties(o,vo);vo.setReturnRequest(rs.get(o.getId()));var u=us.get(o.getUserId());if(u!=null)vo.setUsername(u.getNickname()!=null?u.getNickname():u.getUsername());var d=ds.get(o.getDroneId());if(d!=null){vo.setDroneModel(d.getModel());vo.setDroneImage(d.getImage());}var a=o.getAirspaceRecordId()==null?null:regions.get(o.getAirspaceRecordId());if(a!=null)vo.setRegionName(a.getRegionName());return vo;}).toList();
 }
 public List<MaintenanceTicketVO> tickets(List<MaintenanceTicket> page){
  if(page.isEmpty())return List.of();var us=users(ids(page,MaintenanceTicket::getUserId));var ds=drones(ids(page,MaintenanceTicket::getDroneId));var fs=ids(page,MaintenanceTicket::getFaultReportId);
  var reports=fs.isEmpty()?Map.<Long,FaultReport>of():index(faults.selectList(new LambdaQueryWrapper<FaultReport>().select(FaultReport::getId,FaultReport::getFaultType).in(FaultReport::getId,fs)),FaultReport::getId);
  return page.stream().map(t->{var vo=new MaintenanceTicketVO();BeanUtils.copyProperties(t,vo);var u=t.getUserId()==null?null:us.get(t.getUserId());if(u!=null)vo.setUserName(u.getUsername());var d=ds.get(t.getDroneId());if(d!=null){vo.setDroneModel(d.getModel());vo.setDroneBrand(d.getBrand());}var f=reports.get(t.getFaultReportId());if(f!=null)vo.setFaultType(f.getFaultType());return vo;}).toList();
 }
 public static void page(int page,int size){if(page<1||size<1||size>100)throw new com.drone.rental.common.exception.BusinessException(400,"页码至少1，每页1至100条");}
}
