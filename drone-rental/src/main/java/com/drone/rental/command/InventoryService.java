package com.drone.rental.command;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.drone.rental.entity.Drone;
import com.drone.rental.common.exception.BusinessException;
@Service
public class InventoryService {
 private final JdbcTemplate jdbc;private final EventRecorder events;private final FaultPoints faults;
 public InventoryService(JdbcTemplate jdbc,EventRecorder events,FaultPoints faults){this.jdbc=jdbc;this.events=events;this.faults=faults;}
 private void assertLocked(long device){if(!Long.valueOf(device).equals(CommandContext.required().device))throw new IllegalStateException("Device lock protocol violated");}
 public int active(long device){return jdbc.queryForObject("SELECT COUNT(*) FROM maintenance_ticket WHERE drone_id=? AND status IN(0,1,4) AND deleted=0",Integer.class,device);}
 public void validateAdminState(Drone d){
  if(d.getStock()==null || d.getStock()<0)throw new BusinessException(400,"库存不能为负");
  if(d.getStatus()==null || d.getStatus()<0 || d.getStatus()>2)throw new BusinessException(400,"无效设备状态");
  if(active(d.getId()==null?-1:d.getId())>0 && d.getStatus()!=2)throw new BusinessException(409,"活动维修未验收，不能恢复可租");
  if(d.getStatus()!=2)d.setStatus(d.getStock()>0?1:0);
 }
 public void change(long device,int amount,long order,boolean allocate){
  assertLocked(device);if(amount!=1)throw new BusinessException(400,"当前订单只支持一件型号库存");
  var o=jdbc.queryForMap("SELECT drone_id,inventory_held FROM rental_order WHERE id=? FOR UPDATE",order);
  if(((Number)o.get("drone_id")).longValue()!=device)throw new BusinessException(409,"库存分配与订单不匹配");
  if(((Number)o.get("inventory_held")).intValue()!=1)throw new BusinessException(409,"该分配已释放");
  int before=jdbc.queryForObject("SELECT stock FROM drone WHERE id=? FOR UPDATE",Integer.class,device);
  if(allocate && before<amount)throw new BusinessException(409,"库存不足");
  if(!allocate && jdbc.update("UPDATE rental_order SET inventory_held=0 WHERE id=? AND inventory_held=1",order)!=1)throw new BusinessException(409,"库存已释放");
  int delta=allocate?-amount:amount;
  if(jdbc.update("UPDATE drone SET stock=stock+? WHERE id=? AND stock+?>=0",delta,device,delta)!=1)throw new BusinessException(409,"库存冲突");
  faults.at(allocate?"after_allocation":"after_release",CommandContext.required());
  int after=before+delta;
  jdbc.update("UPDATE drone SET status=CASE WHEN status=2 THEN 2 WHEN ?>0 THEN 1 ELSE 0 END WHERE id=?",after,device);
  stockLog(device,allocate?2:3,delta,before,after,order,allocate?"ALLOCATE":"RELEASE",allocate?"ALLOCATE":"RELEASE");
 }
 public void stockLog(long device,int changeType,int delta,int before,int after,Long order,String event,String effect){
  var c=CommandContext.required();
  jdbc.update("INSERT INTO drone_stock_log(drone_id,change_type,change_amount,before_stock,after_stock,related_order_id,operator_id,command_id,event_type,inventory_effect) VALUES (?,?,?,?,?,?,?,?,?,?)",device,changeType,delta,before,after,order,c.actor,c.id,event,effect);
  events.record("DRONE",device,event,before,after);
 }
 public void status(long device,int target){
  if(target<0 || target>2)throw new BusinessException(400,"无效设备状态");
  assertLocked(device);int old=jdbc.queryForObject("SELECT status FROM drone WHERE id=? FOR UPDATE",Integer.class,device);
  int stock=jdbc.queryForObject("SELECT stock FROM drone WHERE id=?",Integer.class,device);
  if(target!=2 && active(device)>0)throw new BusinessException(409,"仍有活动维修");
  if(target!=2)target=stock>0?1:0;
  if(old!=target){if(jdbc.update("UPDATE drone SET status=? WHERE id=? AND status=?",target,device,old)!=1)throw new BusinessException(409,"设备状态冲突");events.record("DRONE",device,"DEVICE_STATUS",old,target);}
 }
}
