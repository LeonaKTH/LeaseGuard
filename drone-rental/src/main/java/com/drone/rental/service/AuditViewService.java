package com.drone.rental.service;
import com.drone.rental.security.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
@Service
public class AuditViewService {
 private final JdbcTemplate jdbc;private final OrderService orders;private final MaintenanceTicketService repairs;
 public AuditViewService(JdbcTemplate j,OrderService o,MaintenanceTicketService r){jdbc=j;orders=o;repairs=r;}
 public record Event(String id,String aggregateType,String resourceId,String eventType,Integer fromStatus,Integer toStatus,String actorId,String actorName,String actorKind,LocalDateTime createdAt,String previousAssigneeId,String assigneeId){}
 public Page<Event> events(String kind,long id,int page,int size){
  ListAssembler.page(page,size);List<Object> args=new ArrayList<>();String scope;
  if(kind.equals("orders")){Roles.customer();orders.getOrderDetail(id);scope="((x.aggregate_type='ORDER' AND x.aggregate_id=?) OR(x.aggregate_type='RETURN' AND x.aggregate_id IN(SELECT id FROM return_request WHERE order_id=?)) OR(x.aggregate_type='FAULT' AND x.aggregate_id IN(SELECT id FROM fault_report WHERE order_id=?)) OR(x.aggregate_type='TICKET' AND x.aggregate_id IN(SELECT t.id FROM maintenance_ticket t JOIN fault_report f ON f.id=t.fault_report_id WHERE f.order_id=?)))";for(int i=0;i<4;i++)args.add(id);}
  else if(kind.equals("tickets")){var t=repairs.getTicketDetail(id);scope="((x.aggregate_type='TICKET' AND x.aggregate_id=?) OR(x.aggregate_type='FAULT' AND x.aggregate_id=?))";args.add(id);args.add(t.getFaultReportId());if(Integer.valueOf(2).equals(UserContext.getCurrentRole())){scope+=" AND EXISTS(SELECT 1 FROM maintenance_ticket st WHERE st.id=? AND st.assignee_id=? AND st.deleted=0)";args.add(id);args.add(UserContext.getCurrentUserId());}}
  else throw new com.drone.rental.common.exception.BusinessException(400,"未知审计资源");
  String where=" WHERE e.command_id IN(SELECT x.command_id FROM business_event x WHERE "+scope+")";
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM business_event e"+where,Long.class,args.toArray());
  args.add(size);args.add((page-1)*size);String select="SELECT e.*,u.username,u.principal_kind,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.details,'$.fromAssigneeId')),'null') AS old_assignee,NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.details,'$.toAssigneeId')),'null') AS new_assignee FROM business_event e JOIN user u ON u.id=e.actor_id";
  var result=new Page<Event>(page,size,total);result.setRecords(jdbc.query(select+where+" ORDER BY e.id DESC LIMIT ? OFFSET ?",(r,n)->new Event(r.getString("id"),r.getString("aggregate_type"),r.getString("aggregate_id"),r.getString("event_type"),(Integer)r.getObject("from_status"),(Integer)r.getObject("to_status"),r.getString("actor_id"),r.getString("username"),r.getString("principal_kind"),r.getObject("created_at",LocalDateTime.class),r.getString("old_assignee"),r.getString("new_assignee")),args.toArray()));return result;
 }
 public record Task(String id,String orderId,String type,String status,int attempts,int maxAttempts,String lastError,String result,LocalDateTime dueAt,LocalDateTime createdAt,LocalDateTime updatedAt){}
 public Page<Task> tasks(int page,int size,String status){Access.admin();ListAssembler.page(page,size);List<Object> args=new ArrayList<>();String where="";if(status!=null&&!status.isBlank()){if(!Set.of("PENDING","CLAIMED","DONE","FAILED").contains(status))throw new com.drone.rental.common.exception.BusinessException(400,"无效任务状态");where=" WHERE status=?";args.add(status);}long total=jdbc.queryForObject("SELECT COUNT(*) FROM job_task"+where,Long.class,args.toArray());args.add(size);args.add((page-1)*size);var out=new Page<Task>(page,size,total);out.setRecords(jdbc.query("SELECT id,order_id,type,status,attempts,max_attempts,last_error,result,due_at,created_at,updated_at FROM job_task"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",(r,n)->new Task(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6),r.getString(7),r.getString(8),r.getObject(9,LocalDateTime.class),r.getObject(10,LocalDateTime.class),r.getObject(11,LocalDateTime.class)),args.toArray()));return out;}
}
