package com.drone.rental;

import com.drone.rental.command.*;
import com.drone.rental.common.exception.*;
import com.drone.rental.dto.*;
import com.drone.rental.entity.*;
import com.drone.rental.mapper.*;
import com.drone.rental.security.*;
import com.drone.rental.service.*;
import com.drone.rental.vo.OrderVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import javax.sql.DataSource;
import java.sql.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Independent committed commands: deliberately NO @Transactional on this test class. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.hikari.maximum-pool-size=20","spring.datasource.hikari.minimum-idle=20","leaseguard.command.lock-wait-seconds=5"})
@Import(ConcurrencyG2IT.Probes.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrencyG2IT {
 @TestConfiguration static class Probes {@Bean @Primary HarnessHooks harness(JdbcTemplate jdbc){return new HarnessHooks(jdbc);}}
 static class HarnessHooks implements CommandHooks {
  final JdbcTemplate jdbc;volatile CyclicBarrier gate;volatile Consumer<CommandContext> deviceAction;
  volatile String failAt;volatile boolean checked,timeoutMode;volatile CountDownLatch committed,releaseResponse;
  final Set<Long> connections=ConcurrentHashMap.newKeySet();final Set<Integer> attempts=ConcurrentHashMap.newKeySet();
  final AtomicInteger active=new AtomicInteger(),peak=new AtomicInteger();
  HarnessHooks(JdbcTemplate jdbc){this.jdbc=jdbc;}
  public void at(String point,CommandContext c)throws Exception{
   if(point.equals("connected")){
    assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
    jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{assertFalse(connection.getAutoCommit());return null;});
    // A compatible actor S-lock proves InnoDB is engaged before the cohort barrier.
    // No drone/order/key row is locked by the probe; production has no hook bean.
    assertEquals(c.actor,jdbc.queryForObject("SELECT id FROM user WHERE id=? FOR SHARE",Long.class,c.actor));
    connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));attempts.add(c.attempt);
    int n=active.incrementAndGet();peak.accumulateAndGet(n,Math::max);
    if(timeoutMode)jdbc.execute("SET SESSION innodb_lock_wait_timeout=1");
    var b=gate;if(b!=null && c.attempt==1)b.await(20,TimeUnit.SECONDS);
   }
   if(point.equals("finished"))active.decrementAndGet();
   if(point.equals("device_locked") && deviceAction!=null)deviceAction.accept(c);
   if(point.equals(failAt)){if(checked)throw new java.io.IOException("controlled checked interruption");throw new IllegalStateException("controlled interruption");}
   if(point.equals("after_commit") && committed!=null){committed.countDown();assertTrue(releaseResponse.await(10,TimeUnit.SECONDS));}
  }
  void reset(){gate=null;deviceAction=null;failAt=null;checked=false;timeoutMode=false;committed=null;releaseResponse=null;connections.clear();attempts.clear();active.set(0);peak.set(0);}
 }
 @Autowired JdbcTemplate jdbc;@Autowired DataSource dataSource;@Autowired HarnessHooks hooks;@Autowired CommandExecutor executor;
 @Autowired OrderService orders;@Autowired FaultReportService faults;@Autowired MaintenanceTicketService repairs;@Autowired DroneService drones;
 @Autowired JwtUtil jwt;@Autowired UserMapper users;@Autowired ObjectMapper json;@LocalServerPort int port;
 long user,admin;String run="G2"+UUID.randomUUID().toString().replace("-","").substring(0,10);
 final List<Map<String,Object>> facts=new ArrayList<>();
 record F(long device,Long order,int initialTotal){}
 record Outcome(String result,Long resource,Integer code){}
 @BeforeAll void setup() throws Exception {
  assertEquals("leaseguard_test",jdbc.queryForObject("SELECT DATABASE()",String.class));assertEquals(20,dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize());
  user=insert("INSERT INTO user(username,password,role) VALUES (?,'unusable-g2-test',0)",run+"u");admin=insert("INSERT INTO user(username,password,role) VALUES (?,'unusable-g2-test',1)",run+"a");
  jdbc.update("INSERT INTO user_qualification(user_id,certificate_no,valid_start_date,valid_end_date,audit_status) VALUES (?,?,'2020-01-01','2099-12-31',1)",user,run);
 }
 long insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},key);return Objects.requireNonNull(key.getKey()).longValue();}
 long n(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
 String label(){return run+UUID.randomUUID().toString().replace("-","").substring(0,12);}
 F fixture(int available,Integer state){
  long d=insert("INSERT INTO drone(model,price_per_day,stock,status,on_shelf) VALUES (?,12.50,?,CASE WHEN ?>0 THEN 1 ELSE 0 END,1)",label(),available,available);Long o=null;
  if(state!=null){
   String no=label();o=insert("INSERT INTO rental_order(order_no,user_id,drone_id,rental_start_time,rental_end_time,rental_days,unit_price,total_amount,order_status) VALUES (?,?,?,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),2,12.50,25,?)",no,user,d,state);
   insert("INSERT INTO payment(payment_no,order_id,order_no,user_id,amount,payment_status) VALUES (?,?,?,?,25,?)",label(),o,no,user,state==0?0:1);
   jdbc.update("INSERT INTO drone_stock_log(drone_id,change_type,change_amount,before_stock,after_stock,related_order_id,inventory_effect,event_type) VALUES (?,2,-1,?,?,?,'ALLOCATE','FIXTURE_ALLOCATE')",d,available+1,available,o);
  }
  return new F(d,o,available+(state==null?0:1));
 }
 OrderCreateDTO dto(long device,int days){var d=new OrderCreateDTO();d.setDroneId(device);d.setStartDate(LocalDate.now().plusDays(1));d.setEndDate(LocalDate.now().plusDays(days));d.setRemark("G2 synthetic");return d;}
 void identity(boolean isAdmin){UserContext.setCurrentUser(new UserContext.UserInfo(isAdmin?admin:user,"g2",isAdmin?1:0));}
 Object action(boolean isAdmin,String key,Callable<?> work)throws Exception{identity(isAdmin);CommandKeys.bind(key);try{return work.call();}finally{CommandKeys.clear();UserContext.clear();}}
 void gold(F f){
  long stock=n("SELECT stock FROM drone WHERE id=?",f.device);assertTrue(stock>=0);
  long sum=n("SELECT COALESCE(SUM(change_amount),0) FROM drone_stock_log WHERE drone_id=?",f.device);assertEquals(f.initialTotal+sum,stock,"Frozen stock conservation");
  long cursor=f.initialTotal;
  for(var l:jdbc.queryForList("SELECT * FROM drone_stock_log WHERE drone_id=? ORDER BY id",f.device)){
   assertEquals(cursor,((Number)l.get("before_stock")).longValue());cursor+=((Number)l.get("change_amount")).longValue();assertEquals(cursor,((Number)l.get("after_stock")).longValue());
   if(l.get("command_id")!=null)assertEquals(1,n("SELECT COUNT(*) FROM business_event WHERE command_id=? AND aggregate_type='DRONE' AND aggregate_id=? AND event_type=? AND from_status=? AND to_status=?",l.get("command_id"),f.device,l.get("event_type"),l.get("before_stock"),l.get("after_stock")));
  }
  assertEquals(stock,cursor);
  assertEquals(0,n("SELECT COUNT(*) FROM (SELECT related_order_id,inventory_effect FROM drone_stock_log WHERE drone_id=? AND inventory_effect IS NOT NULL GROUP BY related_order_id,inventory_effect HAVING COUNT(*)>1) x",f.device));
  assertEquals(0,n("SELECT COUNT(*) FROM drone_stock_log l JOIN command_dedup c ON c.id=l.command_id WHERE l.drone_id=? AND c.response_json IS NULL",f.device));
 }
 List<Outcome> batch(String scenario,F f,int round,IntPredicate asAdmin,boolean sameKey,BiFunction<Integer,String,Object> work)throws Exception{
  hooks.reset();hooks.gate=new CyclicBarrier(20);String key=label();var before=executor.metrics();var pool=Executors.newFixedThreadPool(20);List<Future<Outcome>> tasks=new ArrayList<>();
  for(int i=0;i<20;i++){final int index=i;tasks.add(pool.submit(()->{try{Object r=action(asAdmin.test(index),sameKey?key:key+"-"+index,()->work.apply(index,key));return new Outcome("success",r instanceof OrderVO o?o.getId():null,200);}catch(BusinessException e){return new Outcome(GlobalExceptionHandler.status(e.getCode())==409?"conflict":"unexpected",null,e.getCode());}catch(Throwable e){return new Outcome("unexpected",null,500);}}));}
  List<Outcome> result=new ArrayList<>();try{for(var t:tasks)result.add(t.get(45,TimeUnit.SECONDS));}finally{pool.shutdownNow();hooks.gate=null;}
  Map<String,Object> record=new LinkedHashMap<>();record.put("scenario",scenario);record.put("round",round);record.put("device_id",f.device);record.put("threads",20);record.put("distinct_connections",hooks.connections.size());record.put("peak_connected_transactions",hooks.peak.get());record.put("connection_ids",new TreeSet<>(hooks.connections));record.put("success",result.stream().filter(o->o.result.equals("success")).count());record.put("conflict",result.stream().filter(o->o.result.equals("conflict")).count());record.put("unexpected",result.stream().filter(o->o.result.equals("unexpected")).count());record.put("metrics_before",before);record.put("metrics_after",executor.metrics());facts.add(record);save();
  assertEquals(20,hooks.connections.size(),"Must acquire 20 real independent connections");assertEquals(20,hooks.peak.get());assertEquals(0,result.stream().filter(o->o.result.equals("unexpected")).count(),record.toString());gold(f);record.put("gold_passed",true);save();return result;
 }
 void save(){try{Path path=Path.of(System.getenv("LEASEGUARD_EVIDENCE_DIR"),"concurrency-"+run+".json");json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),Map.of("git_sha",System.getenv("LEASEGUARD_TEST_SHA"),"mysql",jdbc.queryForObject("SELECT VERSION()",String.class),"pool_max",20,"probe","each connection has autoCommit=false and holds a compatible actor InnoDB shared row lock before the barrier","run",run,"facts",facts));}catch(Exception e){throw new IllegalStateException(e);}}
 long successes(List<Outcome> r){return r.stream().filter(o->o.result.equals("success")).count();}
 @AfterEach void clear(){hooks.reset();CommandKeys.clear();UserContext.clear();save();}
 @Test @Order(1) void lastItemTwentyIndependentCommandsThirtyRounds()throws Exception{
  for(int r=1;r<=30;r++){F f=fixture(1,null);var out=batch("last_item",f,r,i->false,false,(i,k)->orders.createOrder(dto(f.device,2)));assertEquals(1,successes(out));assertEquals(0,n("SELECT stock FROM drone WHERE id=?",f.device));assertEquals(1,n("SELECT COUNT(*) FROM rental_order WHERE drone_id=?",f.device));}
 }
 @Test @Order(2) void cancelTwentyDifferentKeysThirtyRounds()throws Exception{
  for(int r=1;r<=30;r++){F f=fixture(0,0);var out=batch("cancel",f,r,i->false,false,(i,k)->{orders.cancelOrder(f.order,"G2");return null;});assertEquals(1,successes(out));assertEquals(5,n("SELECT order_status FROM rental_order WHERE id=?",f.order));assertEquals(1,n("SELECT stock FROM drone WHERE id=?",f.device));assertEquals(0,n("SELECT inventory_held FROM rental_order WHERE id=?",f.order));}
 }
 @Test @Order(3) void refundTwentyDifferentKeysThirtyRounds()throws Exception{
  for(int r=1;r<=30;r++){F f=fixture(0,1);var out=batch("refund",f,r,i->true,false,(i,k)->{orders.refundOrder(f.order,"G2");return null;});assertEquals(1,successes(out));assertEquals(6,n("SELECT order_status FROM rental_order WHERE id=?",f.order));assertEquals(2,n("SELECT payment_status FROM payment WHERE order_id=?",f.order));}
 }
 @Test @Order(4) void paymentAndCancelRaceThirtyRounds()throws Exception{
  for(int r=1;r<=30;r++){F f=fixture(0,0);var out=batch("pay_cancel",f,r,i->false,false,(i,k)->{if(i%2==0)orders.simulatePay(f.order,"synthetic");else orders.cancelOrder(f.order,"G2");return null;});assertEquals(1,successes(out));long state=n("SELECT order_status FROM rental_order WHERE id=?",f.order);assertTrue(state==1||state==5);assertEquals(state==5?1:0,n("SELECT stock FROM drone WHERE id=?",f.device));assertEquals(state==1?1:0,n("SELECT payment_status FROM payment WHERE order_id=?",f.order));}
 }
 @Test @Order(5) void returnReviewTwentyDifferentKeysThirtyRounds()throws Exception{
  for(int r=1;r<=30;r++){F f=fixture(0,3);jdbc.update("INSERT INTO return_request(order_id,user_id,submitted_at) VALUES (?,?,NOW())",f.order,user);var out=batch("return_review",f,r,i->true,false,(i,k)->{orders.reviewReturn(f.order,true,"G2");return null;});assertEquals(1,successes(out));assertEquals(4,n("SELECT order_status FROM rental_order WHERE id=?",f.order));assertEquals(1,n("SELECT status FROM return_request WHERE order_id=?",f.order));}
 }
 long fault(F f,int status){return insert("INSERT INTO fault_report(report_no,user_id,drone_id,order_id,fault_description,audit_status) VALUES (?,?,?,?,'G2 synthetic',?)",label(),user,f.device,f.order,status);}
 @Test @Order(6) void faultApprovalCreatesOnlyOneTicketThirtyRounds()throws Exception{
  for(int r=1;r<=30;r++){F f=fixture(1,3);long fault=fault(f,0);var out=batch("fault_audit",f,r,i->true,false,(i,k)->{var a=new AuditDTO();a.setAuditStatus(1);faults.auditFaultReport(fault,a);return null;});assertEquals(1,successes(out));assertEquals(1,n("SELECT COUNT(*) FROM maintenance_ticket WHERE fault_report_id=?",fault));assertEquals(2,n("SELECT status FROM drone WHERE id=?",f.device));}
 }
 long waitingTicket(F f){long fault=fault(f,1);jdbc.update("UPDATE drone SET status=2 WHERE id=?",f.device);return insert("INSERT INTO maintenance_ticket(ticket_no,fault_report_id,drone_id,user_id,status,complete_time) VALUES (?,?,?,?,4,NOW())",label(),fault,f.device,user);}
 @Test @Order(7) void acceptanceAndNewRentalLinearizeThirtyRounds()throws Exception{
  for(int r=1;r<=30;r++){F f=fixture(1,3);long ticket=waitingTicket(f);var out=batch("repair_rent",f,r,i->i==0,false,(i,k)->{if(i==0){repairs.acceptMaintenance(ticket,"G2");return null;}return orders.createOrder(dto(f.device,2));});assertEquals(2,n("SELECT status FROM maintenance_ticket WHERE id=?",ticket));long allocations=n("SELECT COUNT(*) FROM rental_order WHERE drone_id=?",f.device)-1;assertTrue(allocations==0||allocations==1);assertEquals(1+allocations,successes(out));assertEquals(1-allocations,n("SELECT stock FROM drone WHERE id=?",f.device));
   if(allocations==1)assertTrue(n("SELECT MIN(id) FROM business_event WHERE aggregate_type='TICKET' AND aggregate_id=? AND event_type='ACCEPT_REPAIR'",ticket)<n("SELECT MIN(e.id) FROM business_event e JOIN rental_order o ON e.aggregate_type='ORDER' AND e.aggregate_id=o.id WHERE o.drone_id=? AND e.event_type='ORDER_CREATED'",f.device));
  }
 }
 @Test @Order(8) void sameKeyTwentyCreatesReplayOneResourceThirtyRounds()throws Exception{
  for(int r=1;r<=30;r++){F f=fixture(1,null);var out=batch("same_key",f,r,i->false,true,(i,k)->orders.createOrder(dto(f.device,2)));assertEquals(20,successes(out));assertEquals(1,out.stream().map(Outcome::resource).distinct().count());assertEquals(1,n("SELECT COUNT(*) FROM rental_order WHERE drone_id=?",f.device));}
 }
 @Test @Order(9) void sameKeyDifferentParametersConflictThirtyRounds()throws Exception{
  for(int r=1;r<=30;r++){F f=fixture(2,null);var out=batch("key_hash_conflict",f,r,i->false,true,(i,k)->orders.createOrder(dto(f.device,i%2+1)));assertEquals(10,successes(out));assertEquals(1,n("SELECT COUNT(*) FROM rental_order WHERE drone_id=?",f.device));assertEquals(1,n("SELECT stock FROM drone WHERE id=?",f.device));}
 }
 @Test @Order(10) void checkedAndRuntimeFaultsRollbackThirtyTransactions()throws Exception{
  for(int r=0;r<30;r++){F f=fixture(1,null);String key=label();hooks.failAt=r%2==0?"after_allocation":"before_receipt";hooks.checked=r%3==0;
   assertThrows(RuntimeException.class,()->action(false,key,()->orders.createOrder(dto(f.device,2))));hooks.failAt=null;
   assertEquals(1,n("SELECT stock FROM drone WHERE id=?",f.device));assertEquals(0,n("SELECT COUNT(*) FROM rental_order WHERE drone_id=?",f.device));assertEquals(0,n("SELECT COUNT(*) FROM drone_stock_log WHERE drone_id=?",f.device));assertEquals(0,n("SELECT COUNT(*) FROM command_dedup WHERE actor_id=? AND operation='CREATE_ORDER' AND request_key=?",user,key));assertEquals(0,n("SELECT COUNT(*) FROM business_event WHERE aggregate_type='DRONE' AND aggregate_id=?",f.device));
   action(false,key,()->orders.createOrder(dto(f.device,2)));gold(f);
  }facts.add(Map.of("scenario","rollback_fault_injection","transactions",30,"gold_passed",true));
 }
 @Test @Order(11) void waitingRepairCannotBeBypassedAndOtherActiveRepairBlocksRelease()throws Exception{
  F f=fixture(1,3);long first=waitingTicket(f),second=waitingTicket(f);
  assertThrows(BusinessException.class,()->action(true,label(),()->{drones.updateStatus(f.device,1);return null;}));
  action(true,label(),()->{repairs.acceptMaintenance(first,"G2");return null;});assertEquals(2,n("SELECT status FROM drone WHERE id=?",f.device));
  action(true,label(),()->{repairs.acceptMaintenance(second,"G2");return null;});assertEquals(1,n("SELECT status FROM drone WHERE id=?",f.device));gold(f);
 }
 @Test @Order(12) void lockWaitTimeoutRetriesThreeWholeTransactionsWithoutResidue()throws Exception{
  F f=fixture(1,null);hooks.timeoutMode=true;var before=executor.metrics();String key=label();
  try(Connection blocker=dataSource.getConnection()){
   blocker.setAutoCommit(false);try(var s=blocker.prepareStatement("SELECT id FROM drone WHERE id=? FOR UPDATE")){s.setLong(1,f.device);s.executeQuery().close();}
   BusinessException ex=assertThrows(BusinessException.class,()->action(false,key,()->orders.createOrder(dto(f.device,2))));assertEquals(503,ex.getCode());blocker.rollback();
  }
  assertEquals(Set.of(1,2,3),hooks.attempts);assertEquals(2,executor.metrics().get("retries")-before.get("retries"));assertEquals(3,executor.metrics().get("timeouts")-before.get("timeouts"));assertEquals(0,n("SELECT COUNT(*) FROM command_dedup WHERE request_key=?",key));gold(f);
  facts.add(Map.of("scenario","real_lock_timeout","attempts",3,"new_transactions",3,"final_http_semantic",503,"gold_passed",true));
 }
 @Test @Order(13) void realInnoDbDeadlockHasBoundedRecovery()throws Exception{
  F f=fixture(0,0);List<Long> ballast=new ArrayList<>();for(int i=0;i<100;i++)ballast.add(fixture(0,null).device);
  var locked=new CountDownLatch(1);hooks.deviceAction=c->{if(c.device.equals(f.device)&&c.attempt==1)locked.countDown();};var before=executor.metrics();var pool=Executors.newSingleThreadExecutor();String key=label();
  int blockerDeadlocks=0;
  try(Connection blocker=dataSource.getConnection()){
   blocker.setAutoCommit(false);try(var s=blocker.createStatement()){
    s.executeUpdate("UPDATE drone SET description='G2 adversarial lock holder' WHERE id IN("+ballast.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+")");
    s.executeQuery("SELECT id FROM rental_order WHERE id="+f.order+" FOR UPDATE").close();
    var future=pool.submit(()->action(false,key,()->{orders.cancelOrder(f.order,"G2");return null;}));assertTrue(locked.await(10,TimeUnit.SECONDS));
    try{s.executeQuery("SELECT id FROM drone WHERE id="+f.device+" FOR UPDATE").close();blocker.commit();}catch(SQLException ex){assertEquals(1213,ex.getErrorCode());blockerDeadlocks++;blocker.rollback();}
    future.get(15,TimeUnit.SECONDS);
   }
  }finally{pool.shutdownNow();}
  long seen=executor.metrics().get("deadlocks")-before.get("deadlocks");assertTrue(seen+blockerDeadlocks>=1);assertTrue(executor.metrics().get("retries")-before.get("retries")<=2);assertEquals(5,n("SELECT order_status FROM rental_order WHERE id=?",f.order));gold(f);
  facts.add(Map.of("scenario","real_deadlock","command_deadlocks",seen,"adversary_deadlocks",blockerDeadlocks,"max_command_attempts",3,"gold_passed",true));
 }
 @Test @Order(14) void committedCommandSurvivesActualHttpReadTimeout()throws Exception{
  F f=fixture(1,null);String key=label();hooks.committed=new CountDownLatch(1);hooks.releaseResponse=new CountDownLatch(1);
  User u=users.selectById(user);String token=jwt.generateToken(u.getId(),u.getUsername(),u.getRole(),u.getPassword());
  byte[] payload=json.writeValueAsBytes(dto(f.device,2));var pool=Executors.newSingleThreadExecutor();
  try{
   Future<?> lost=pool.submit(()->{try{var c=(java.net.HttpURLConnection)new java.net.URI("http://127.0.0.1:"+port+"/api/order/create").toURL().openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setReadTimeout(800);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Idempotency-Key",key);c.getOutputStream().write(payload);assertThrows(java.net.SocketTimeoutException.class,()->c.getInputStream());c.disconnect();}catch(Exception e){throw new RuntimeException(e);}});
   assertTrue(hooks.committed.await(10,TimeUnit.SECONDS));lost.get(10,TimeUnit.SECONDS);assertEquals(1,n("SELECT COUNT(*) FROM rental_order WHERE drone_id=?",f.device));hooks.releaseResponse.countDown();hooks.committed=null;
   var c=(java.net.HttpURLConnection)new java.net.URI("http://127.0.0.1:"+port+"/api/order/create").toURL().openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Idempotency-Key",key);c.getOutputStream().write(payload);assertEquals(200,c.getResponseCode());var receipt=json.readTree(c.getInputStream());assertEquals(n("SELECT id FROM rental_order WHERE drone_id=?",f.device),receipt.path("data").path("id").asLong());c.disconnect();gold(f);
  }finally{if(hooks.releaseResponse!=null)hooks.releaseResponse.countDown();pool.shutdownNow();}
  facts.add(Map.of("scenario","http_committed_response_lost","socket_read_timeout",true,"same_key_http_replay",200,"gold_passed",true));
 }
 @Test @Order(18) void allRetainedBusinessFactsReconcileWithCommandsAndEvents(){
  long missingAllocations=n("SELECT COUNT(*) FROM drone_stock_log r JOIN drone d ON d.id=r.drone_id LEFT JOIN drone_stock_log a ON a.related_order_id=r.related_order_id AND a.inventory_effect='ALLOCATE' WHERE d.model LIKE ? AND r.inventory_effect='RELEASE' AND a.id IS NULL",run+"%");
  long heldMismatch=n("SELECT COUNT(*) FROM rental_order o JOIN drone d ON d.id=o.drone_id WHERE d.model LIKE ? AND ((o.order_status IN(0,1,2,3) AND o.inventory_held<>1) OR (o.order_status IN(4,5,6) AND o.inventory_held<>0))",run+"%");
  long eventMismatch=n("SELECT COUNT(*) FROM business_event e JOIN command_dedup c ON c.id=e.command_id WHERE c.actor_id IN(?,?) AND c.actor_id<>e.actor_id",user,admin);
  long badSequence=n("SELECT COUNT(*) FROM (SELECT e.command_id FROM business_event e JOIN command_dedup c ON c.id=e.command_id WHERE c.actor_id IN(?,?) GROUP BY e.command_id HAVING MIN(e.seq_no)<>1 OR MAX(e.seq_no)<>COUNT(*)) x",user,admin);
  long receiptMismatch=n("SELECT COUNT(*) FROM command_dedup c WHERE c.actor_id IN(?,?) AND (c.response_json IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(c.response_json,'$.operation'))<>c.operation)",user,admin);
  long maintenanceMismatch=n("SELECT COUNT(*) FROM drone d WHERE d.model LIKE ? AND d.status<>2 AND EXISTS(SELECT 1 FROM maintenance_ticket t WHERE t.drone_id=d.id AND t.status IN(0,1,4) AND t.deleted=0)",run+"%");
  assertEquals(0,missingAllocations);assertEquals(0,heldMismatch);assertEquals(0,eventMismatch);assertEquals(0,badSequence);assertEquals(0,receiptMismatch);assertEquals(0,maintenanceMismatch);
  facts.add(Map.of("scenario","retained_command_event_audit","missing_allocation",missingAllocations,"held_state_mismatch",heldMismatch,"actor_mismatch",eventMismatch,"sequence_mismatch",badSequence,"receipt_mismatch",receiptMismatch,"maintenance_mismatch",maintenanceMismatch,"gold_passed",true));
 }

 @Test @Order(16) void releaseFailuresRollbackOrderRequestHeldFlagAndLedgerThirtyRounds()throws Exception{
  for(int r=0;r<30;r++){F f=fixture(0,3);jdbc.update("INSERT INTO return_request(order_id,user_id,submitted_at) VALUES (?,?,NOW())",f.order,user);String key=label();hooks.failAt="after_release";hooks.checked=r%2==0;
   assertThrows(RuntimeException.class,()->action(true,key,()->{orders.reviewReturn(f.order,true,"G2 rollback");return null;}));hooks.failAt=null;
   assertEquals(3,n("SELECT order_status FROM rental_order WHERE id=?",f.order));assertEquals(1,n("SELECT inventory_held FROM rental_order WHERE id=?",f.order));assertEquals(0,n("SELECT status FROM return_request WHERE order_id=?",f.order));assertEquals(0,n("SELECT COUNT(*) FROM drone_stock_log WHERE drone_id=? AND inventory_effect='RELEASE'",f.device));assertEquals(0,n("SELECT COUNT(*) FROM command_dedup WHERE request_key=?",key));gold(f);
   action(true,key,()->{orders.reviewReturn(f.order,true,"G2 rollback");return null;});gold(f);
  }facts.add(Map.of("scenario","release_rollback","rounds",30,"gold_passed",true));
 }
 @Test @Order(17) void faultApprovalFailureRollsBackTicketDeviceAndEventsThirtyRounds()throws Exception{
  for(int r=0;r<30;r++){F f=fixture(1,3);long fault=fault(f,0);String key=label();var dto=new AuditDTO();dto.setAuditStatus(1);hooks.failAt="before_receipt";
   assertThrows(RuntimeException.class,()->action(true,key,()->{faults.auditFaultReport(fault,dto);return null;}));hooks.failAt=null;
   assertEquals(0,n("SELECT audit_status FROM fault_report WHERE id=?",fault));assertEquals(0,n("SELECT COUNT(*) FROM maintenance_ticket WHERE fault_report_id=?",fault));assertEquals(1,n("SELECT status FROM drone WHERE id=?",f.device));assertEquals(0,n("SELECT COUNT(*) FROM command_dedup WHERE request_key=?",key));assertEquals(0,n("SELECT COUNT(*) FROM business_event WHERE aggregate_type='DRONE' AND aggregate_id=?",f.device));gold(f);
   action(true,key,()->{faults.auditFaultReport(fault,dto);return null;});assertEquals(1,n("SELECT COUNT(*) FROM maintenance_ticket WHERE fault_report_id=?",fault));gold(f);
  }facts.add(Map.of("scenario","fault_ticket_rollback","rounds",30,"gold_passed",true));
 }

}
