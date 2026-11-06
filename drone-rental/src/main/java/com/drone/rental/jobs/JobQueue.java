package com.drone.rental.jobs;
import com.drone.rental.command.CommandContext;
import com.drone.rental.entity.RentalOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
@Service
public class JobQueue {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final Clock clock;private final JobSettings settings;private final JobFaults faults;
 public JobQueue(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock,JobSettings settings,JobFaults faults){this.jdbc=jdbc;this.clock=clock;this.settings=settings;this.faults=faults;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);}
 public LocalDateTime now(){return LocalDateTime.now(clock);}
 public void enqueue(RentalOrder order){
  if(!TransactionSynchronizationManager.isActualTransactionActive() || !CommandContext.required().operation.equals("CREATE_ORDER"))throw new IllegalStateException("Order and task must share CREATE_ORDER transaction");
  LocalDateTime created=order.getCreatedTime(),due=created.plus(settings.timeout);
  jdbc.update("INSERT INTO job_task(business_key,order_id,type,due_at,available_at,max_attempts,created_command_id,created_at,updated_at) VALUES (?,?,'ORDER_TIMEOUT',?,?,?,?,?,?)","timeout-order-"+order.getId(),order.getId(),due,due,settings.maxAttempts,CommandContext.required().id,created,created);
  faults.hit("after_enqueue",null);
 }
 public Optional<JobClaim> claimNext(){return claimNext(null);}
 /** Specific ID supports controlled operational/test drills; no HTTP endpoint. Normal poller uses no filter. */
 public Optional<JobClaim> claimNext(Long specificId){return tx.execute(s->{
  LocalDateTime now=now();var args=new ArrayList<Object>();args.add(now);args.add(now);if(specificId!=null)args.add(specificId);
  var rows=jdbc.queryForList("SELECT id,order_id,attempts,max_attempts,status FROM job_task WHERE ((status='PENDING' AND available_at<=?) OR(status='CLAIMED' AND lease_until<=?))"+(specificId==null?"":" AND id=?")+" ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",args.toArray());
  if(rows.isEmpty())return Optional.empty();var r=rows.getFirst();long id=((Number)r.get("id")).longValue();int attempts=((Number)r.get("attempts")).intValue(),max=((Number)r.get("max_attempts")).intValue();
  if(attempts>=max){jdbc.update("UPDATE job_task SET status='FAILED',last_error='LEASE_EXPIRED_MAX_ATTEMPTS',lease_until=NULL,claim_token=NULL,updated_at=? WHERE id=?",now,id);return Optional.empty();}
  String token=UUID.randomUUID().toString();LocalDateTime until=now.plus(settings.lease);
  jdbc.update("UPDATE job_task SET last_error=CASE WHEN status='CLAIMED' THEN 'LEASE_EXPIRED' ELSE last_error END,status='CLAIMED',claim_token=?,lease_until=?,attempts=attempts+1,updated_at=? WHERE id=?",token,until,now,id);
  return Optional.of(new JobClaim(id,((Number)r.get("order_id")).longValue(),token,attempts+1,max,until));
 });}
 public boolean owned(JobClaim c){return jdbc.queryForObject("SELECT COUNT(*) FROM job_task WHERE id=? AND status='CLAIMED' AND claim_token=? AND lease_until>?",Integer.class,c.id(),c.token(),now())==1;}
 public boolean done(JobClaim c,TimeoutResult result){return tx.execute(s->jdbc.update("UPDATE job_task SET status='DONE',result=?,completed_at=?,updated_at=?,lease_until=NULL WHERE id=? AND status='CLAIMED' AND claim_token=? AND lease_until>?",result.name(),now(),now(),c.id(),c.token(),now())==1);}
 public boolean failed(JobClaim c,Exception error){
  // Deliberately never persist exception.getMessage()/stacktrace or request data.
  String code=error instanceof com.drone.rental.common.exception.BusinessException b && b.getCode()==503?"DATABASE_BUSY":Thread.currentThread().isInterrupted()?"WORKER_INTERRUPTED":"COMMAND_FAILURE";
  String status=c.attempts()>=c.maxAttempts()?"FAILED":"PENDING";
  return tx.execute(s->jdbc.update("UPDATE job_task SET status=?,last_error=?,available_at=?,lease_until=NULL,updated_at=? WHERE id=? AND status='CLAIMED' AND claim_token=? AND lease_until>?",status,code,now().plus(settings.retryDelay(c.attempts())),now(),c.id(),c.token(),now())==1);
 }
}
