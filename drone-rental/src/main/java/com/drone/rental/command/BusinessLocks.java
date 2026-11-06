package com.drone.rental.command;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.drone.rental.common.exception.BusinessException;
/** The one lock order: command key -> drone -> order -> fault -> ticket -> return request. */
@Service
public class BusinessLocks {
 private final JdbcTemplate jdbc;private final FaultPoints faults;
 public BusinessLocks(JdbcTemplate jdbc,FaultPoints faults){this.jdbc=jdbc;this.faults=faults;}
 private Map<String,Object> one(String table,long id,boolean lock){
  return one(table,id,lock,false);
 }
 private Map<String,Object> one(String table,long id,boolean lock,boolean includeDeleted){
  var rows=jdbc.queryForList("SELECT * FROM "+table+" WHERE id=?"+(includeDeleted?"":" AND deleted=0")+(lock?" FOR UPDATE":""),id);
  if(rows.isEmpty())throw new BusinessException(404,"资源不存在");return rows.getFirst();
 }
 private long id(Map<String,Object> m,String key){return ((Number)m.get(key)).longValue();}
 public void acquire(String target,Long resource){
  if(target.equals("NEW"))return;
  boolean timeout=target.equals("TIMEOUT_ORDER");
  String table=switch(target){case "ORDER","TIMEOUT_ORDER"->"rental_order";case "FAULT"->"fault_report";case "TICKET"->"maintenance_ticket";case "DRONE"->"drone";default->throw new IllegalArgumentException("Unknown lock target");};
  if(resource==null)throw new BusinessException(400,"缺少业务资源");
  Map<String,Object> locate=one(table,resource,false,timeout);
  long device=target.equals("DRONE")?resource:id(locate,"drone_id");one("drone",device,true,timeout);CommandContext.required().device=device;faults.at("device_locked",CommandContext.required());
  Long order=null,fault=null;
  if(target.equals("ORDER") || timeout)order=resource;
  if(target.equals("FAULT")){fault=resource;if(locate.get("order_id")!=null)order=id(locate,"order_id");}
  if(target.equals("TICKET")){
   fault=id(locate,"fault_report_id");var f=one("fault_report",fault,false);if(f.get("order_id")!=null)order=id(f,"order_id");
  }
  if(order!=null){var o=one("rental_order",order,true,timeout);if(id(o,"drone_id")!=device)throw new BusinessException(409,"关联设备已改变");}
  if(fault!=null){var f=one("fault_report",fault,true);if(id(f,"drone_id")!=device)throw new BusinessException(409,"关联设备已改变");}
  var fresh=one(table,resource,true,timeout);
  if(!target.equals("DRONE") && id(fresh,"drone_id")!=device)throw new BusinessException(409,"关联设备已改变");
  if(order!=null)jdbc.queryForList("SELECT id FROM return_request WHERE order_id=? FOR UPDATE",order);
 }
}
