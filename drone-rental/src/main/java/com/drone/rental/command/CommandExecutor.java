package com.drone.rental.command;

import com.drone.rental.common.exception.BusinessException;
import com.drone.rental.security.*;
import com.drone.rental.service.OrderService;
import com.drone.rental.vo.OrderVO;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/** Owns commit/retry boundaries. No same-class @Transactional calls and no retry of business failures. */
@Service
public class CommandExecutor {
 @FunctionalInterface public interface Work<T>{T run() throws Exception;}
 private final JdbcTemplate jdbc;private final PlatformTransactionManager manager;private final BusinessLocks locks;
 private final ObjectMapper json;private final FaultPoints faults;private final ObjectProvider<OrderService> orders;
 @org.springframework.beans.factory.annotation.Autowired private org.mybatis.spring.SqlSessionTemplate sessions;
 private final AtomicLong retries=new AtomicLong(),deadlocks=new AtomicLong(),timeouts=new AtomicLong();
 private static final Set<String> ADMIN=Set.of("ASSIGN_REPAIR","REFUND","CONFIRM_RETURN","REVIEW_RETURN","AUDIT_FAULT","CREATE_TICKET","ACCEPT_REPAIR","EDIT_TICKET","ADD_DRONE","EDIT_DRONE","DELETE_DRONE","SET_STOCK","SET_STATUS","SET_SHELF");
 @Value("${leaseguard.command.lock-wait-seconds:2}") private int lockWait;
 public CommandExecutor(JdbcTemplate jdbc,PlatformTransactionManager manager,BusinessLocks locks,ObjectMapper json,FaultPoints faults,ObjectProvider<OrderService> orders){this.jdbc=jdbc;this.manager=manager;this.locks=locks;this.json=json;this.faults=faults;this.orders=orders;}
 private TransactionTemplate tx(boolean own){var t=new TransactionTemplate(manager);t.setPropagationBehavior(own?TransactionDefinition.PROPAGATION_REQUIRES_NEW:TransactionDefinition.PROPAGATION_REQUIRED);if(own)t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t;}
 public Map<String,Long> metrics(){return Map.of("retries",retries.get(),"deadlocks",deadlocks.get(),"timeouts",timeouts.get());}
 public static String canonical(JsonNode node){
  if(node.isObject()){var out=JsonNodeFactory.instance.objectNode();TreeMap<String,JsonNode> sorted=new TreeMap<>();node.fields().forEachRemaining(e->sorted.put(e.getKey(),e.getValue()));sorted.forEach((k,v)->out.set(k,parseCanonical(v)));return out.toString();}
  if(node.isArray()){var out=JsonNodeFactory.instance.arrayNode();node.forEach(v->out.add(parseCanonical(v)));return out.toString();}
  return node.toString();
 }
 private static JsonNode parseCanonical(JsonNode n){try{return new ObjectMapper().readTree(canonical(n));}catch(Exception e){throw new IllegalArgumentException(e);}}
 private static int transientCode(Throwable error){for(Throwable e=error;e!=null;e=e.getCause())if(e instanceof SQLException s && (s.getErrorCode()==1213 || s.getErrorCode()==1205))return s.getErrorCode();return 0;}
 private static class ClaimCollision extends RuntimeException{ClaimCollision(Throwable e){super(e);}}
 public <T> T execute(String operation,String target,Long resource,Object arguments,Work<T> work){
  if(CommandContext.current()!=null){try{return work.run();}catch(RuntimeException e){throw e;}catch(Exception e){throw new FaultPoints.CommandFailure(e);}}
  Long actor=UserContext.getCurrentUserId();if(actor==null)throw new BusinessException(401,"请先登录");if(ADMIN.contains(operation))Access.admin();
  if(Set.of("START_REPAIR","SUBMIT_REPAIR").contains(operation)){
   Roles.technician();var rows=jdbc.queryForList("SELECT assignee_id FROM maintenance_ticket WHERE id=? AND deleted=0",resource);
   if(rows.isEmpty() || !Objects.equals(rows.getFirst().get("assignee_id"),actor))Access.missing();
  }
  if(Set.of("CREATE_ORDER","PAY","CANCEL","APPLY_RETURN","RECEIVE","REPORT_FAULT").contains(operation))Roles.customer();
  return executeAs(actor,CommandKeys.key(),operation,target,resource,arguments,work);
 }
 public com.drone.rental.jobs.TimeoutResult executeTimeout(com.drone.rental.jobs.SystemActor system,long orderId,Work<com.drone.rental.jobs.TimeoutResult> work){
  if(CommandContext.current()!=null || TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Timeout must be a top-level command");
  return executeAs(system.id(),"timeout-order-"+orderId,"TIMEOUT_CANCEL","TIMEOUT_ORDER",orderId,List.of(orderId),work);
 }
 private <T>T executeAs(long actor,String key,String operation,String target,Long resource,Object arguments,Work<T> work){
  String hash=cn.hutool.crypto.digest.DigestUtil.sha256Hex(canonical(json.valueToTree(arguments)));
  boolean own=!TransactionSynchronizationManager.isActualTransactionActive();
  for(int attempt=1;attempt<=3;attempt++){
   var context=new CommandContext();context.actor=actor;context.operation=operation;context.key=key;context.resource=resource;context.attempt=attempt;
   T result;
   try{
    result=tx(own).execute(status->{
     CommandContext.install(context);
     try{
      jdbc.execute("SET SESSION innodb_lock_wait_timeout="+Math.max(1,Math.min(lockWait,10)));
      faults.at("connected",context);
      KeyHolder holder=new GeneratedKeyHolder();
      try{jdbc.update(c->{var p=c.prepareStatement("INSERT INTO command_dedup(actor_id,operation,request_key,request_hash,resource_id) VALUES (?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);p.setLong(1,actor);p.setString(2,operation);p.setString(3,key);p.setString(4,hash);p.setObject(5,resource);return p;},holder);}catch(DuplicateKeyException e){throw new ClaimCollision(e);}
      context.id=Objects.requireNonNull(holder.getKey()).longValue();
      locks.acquire(target,resource);faults.at("locked",context);
      T value=work.run();if(value instanceof OrderVO order)context.resource=order.getId();
      sessions.clearCache();
      faults.at("before_receipt",context);
      var receipt=new LinkedHashMap<String,Object>();receipt.put("operation",operation);receipt.put("resourceId",context.resource);if(value instanceof com.drone.rental.jobs.TimeoutResult outcome)receipt.put("outcome",outcome.name());
      if(jdbc.update("UPDATE command_dedup SET resource_id=?,response_json=? WHERE id=?",context.resource,json.writeValueAsString(receipt),context.id)!=1)throw new IllegalStateException("Missing command receipt");
      return value;
     }catch(RuntimeException e){throw e;}catch(Exception e){throw new FaultPoints.CommandFailure(e);}finally{try{faults.at("finished",context);}finally{CommandContext.clear();}}
    });
   }catch(ClaimCollision e){
    // execute() has already rolled back the failed insert transaction. Never read in rollback-only state.
    if(!own)throw new BusinessException(409,"外层事务中幂等冲突，请结束该事务后重试");
    return tx(true).execute(status->replay(actor,operation,key,hash));
   }catch(RuntimeException e){
    int code=transientCode(e);if(code==0 || !own)throw e;
    if(code==1213)deadlocks.incrementAndGet();else timeouts.incrementAndGet();
    if(attempt==3)throw new BusinessException(503,"数据库繁忙，请使用相同操作ID重试");
    retries.incrementAndGet();try{Thread.sleep(ThreadLocalRandom.current().nextLong(10,31));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new BusinessException(503,"操作已中断");}
    continue;
   }
   // Runs after commit only. A lost response must NOT roll back or automatically repeat a committed command.
   if(own)faults.at("after_commit",context);
   return result;
  }
  throw new IllegalStateException("Unreachable retry state");
 }
 @SuppressWarnings("unchecked") private <T> T replay(long actor,String operation,String key,String hash){
  var rows=jdbc.queryForList("SELECT request_hash,resource_id,response_json FROM command_dedup WHERE actor_id=? AND operation=? AND request_key=?",actor,operation,key);
  if(rows.isEmpty() || rows.getFirst().get("response_json")==null)throw new BusinessException(503,"原命令结果暂不可读，请重试相同操作ID");
  var row=rows.getFirst();if(!hash.equals(row.get("request_hash")))throw new BusinessException(409,"同一操作ID不能用于不同参数");
  // CREATE replays the same resource, with its current authorized representation; void commands replay success.
  if(operation.equals("CREATE_ORDER"))return (T)orders.getObject().getOrderDetail(((Number)row.get("resource_id")).longValue());
  if(operation.equals("TIMEOUT_CANCEL"))try{return (T)com.drone.rental.jobs.TimeoutResult.valueOf(json.readTree(row.get("response_json").toString()).get("outcome").asText());}catch(Exception ex){throw new BusinessException(503,"原系统命令回执不可读");}
  return null;
 }
}
