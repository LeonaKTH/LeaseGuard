package com.drone.rental;
import com.drone.rental.jobs.*;
import com.drone.rental.command.*;
import com.drone.rental.security.*;
import com.drone.rental.service.OrderService;
import com.drone.rental.dto.OrderCreateDTO;
import com.drone.rental.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Real MySQL committed transactions, no outer @Transactional or mock data source. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"leaseguard.jobs.enabled=false","leaseguard.jobs.timeout-seconds=60","leaseguard.jobs.lease-seconds=10","leaseguard.jobs.max-attempts=3","spring.datasource.hikari.maximum-pool-size=6","spring.datasource.hikari.minimum-idle=2"})
@Import(JobsG3IT.Probes.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobsG3IT {
 static class MutableClock extends Clock {
  final AtomicReference<Instant> instant=new AtomicReference<>(Instant.now());
  public ZoneId getZone(){return ZoneId.of("Asia/Shanghai");}public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}public Instant instant(){return instant.get();}void advance(long seconds){instant.updateAndGet(i->i.plusSeconds(seconds));}
 }
 static class Hooks implements JobHooks,CommandHooks {
  volatile String fail;volatile java.util.function.Consumer<JobClaim> afterBusiness;volatile CyclicBarrier gate;final Set<Long> connections=ConcurrentHashMap.newKeySet();final JdbcTemplate jdbc;
  Hooks(JdbcTemplate jdbc){this.jdbc=jdbc;}
  public void at(String p,JobClaim c)throws Exception{if(p.equals("after_business") && afterBusiness!=null)afterBusiness.accept(c);if(p.equals("claimed"))assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Claim transaction must finish before business");if(p.equals(fail))throw new java.io.IOException("synthetic SECRET_DO_NOT_PERSIST");}
  public void at(String p,CommandContext c)throws Exception{if(p.equals("connected") && gate!=null){assertTrue(TransactionSynchronizationManager.isActualTransactionActive());jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{assertFalse(connection.getAutoCommit());return null;});jdbc.queryForObject("SELECT id FROM user WHERE id=? FOR SHARE",Long.class,c.actor);connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));gate.await(15,TimeUnit.SECONDS);}}
 }
 @TestConfiguration static class Probes {@Bean @Primary MutableClock clock(){return new MutableClock();}@Bean @Primary Hooks hooks(JdbcTemplate jdbc){return new Hooks(jdbc);}}
 @org.springframework.boot.test.web.server.LocalServerPort int port;@Autowired JwtUtil jwt;@Autowired JdbcTemplate jdbc;@Autowired OrderService orders;@Autowired JobQueue queue;@Autowired JobWorker worker;@Autowired SystemActor system;@Autowired MutableClock clock;@Autowired Hooks hooks;@Autowired ObjectMapper json;@Autowired org.springframework.context.ApplicationContext app;
 final String run="G3J"+UUID.randomUUID().toString().replace("-","").substring(0,10);long user,admin;final List<Map<String,Object>> facts=new ArrayList<>();
 record F(long device,long order,long job){}
 long insert(String sql,Object...args){var k=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},k);return Objects.requireNonNull(k.getKey()).longValue();}
 long n(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
 String text(String sql,Object...args){return jdbc.queryForObject(sql,String.class,args);}
 String label(){return run+UUID.randomUUID().toString().replace("-","").substring(0,10);}
 <T>T user(java.util.concurrent.Callable<T> action)throws Exception{UserContext.setCurrentUser(new UserContext.UserInfo(user,run,0));CommandKeys.bind(label());try{return action.call();}finally{UserContext.clear();CommandKeys.clear();}}
 @BeforeAll void setup(){assertEquals("leaseguard_test",text("SELECT DATABASE()"));user=insert("INSERT INTO user(username,password,role) VALUES (?,'unusable-g3-test',0)",run);admin=insert("INSERT INTO user(username,password,role) VALUES (?,'unusable-g3-test',1)",run+"a");jdbc.update("INSERT INTO user_qualification(user_id,certificate_no,valid_start_date,valid_end_date,audit_status) VALUES (?,?,'2020-01-01','2099-12-31',1)",user,run);}
 F create()throws Exception{long d=insert("INSERT INTO drone(model,price_per_day,stock,status,on_shelf) VALUES (?,12.50,1,1,1)",label());return create(d);}
 F create(long device)throws Exception{var dto=new OrderCreateDTO();dto.setDroneId(device);dto.setStartDate(LocalDate.now().plusDays(1));dto.setEndDate(LocalDate.now().plusDays(2));var order=user(()->orders.createOrder(dto));return new F(device,order.getId(),n("SELECT id FROM job_task WHERE order_id=?",order.getId()));}
 void due(){clock.advance(61);}
 JobClaim claim(F f){return queue.claimNext(f.job).orElseThrow();}
 void gold(F f){long state=n("SELECT order_status FROM rental_order WHERE id=?",f.order),held=n("SELECT inventory_held FROM rental_order WHERE id=?",f.order),release=n("SELECT COUNT(*) FROM drone_stock_log WHERE related_order_id=? AND inventory_effect='RELEASE'",f.order);assertEquals(state==5?0:1,held);assertEquals(state==5?1:0,release);assertEquals(state==5?1:0,n("SELECT stock FROM drone WHERE id=?",f.device));assertEquals(1,n("SELECT COUNT(*) FROM drone_stock_log WHERE related_order_id=? AND inventory_effect='ALLOCATE'",f.order));}
 void fact(String scenario,Map<String,Object> details){var f=new LinkedHashMap<String,Object>(details);f.put("scenario",scenario);f.put("gold_passed",true);facts.add(f);}
 @AfterEach void save()throws Exception{hooks.fail=null;hooks.afterBusiness=null;hooks.gate=null;hooks.connections.clear();UserContext.clear();CommandKeys.clear();Files.writeString(Path.of(System.getenv("LEASEGUARD_EVIDENCE_DIR"),"jobs-"+run+".json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("run",run,"source_sha",System.getenv("LEASEGUARD_TEST_SHA"),"mysql",text("SELECT VERSION()"),"scheduler_enabled",false,"facts",facts)));}
 @Test void orderAndTaskAreAtomicAndIdempotent()throws Exception{
  F f=create();assertEquals(1,n("SELECT COUNT(*) FROM job_task WHERE order_id=?",f.order));
  assertEquals(n("SELECT id FROM command_dedup WHERE operation='CREATE_ORDER' AND resource_id=?",f.order),n("SELECT created_command_id FROM job_task WHERE id=?",f.job));
  assertEquals(60,n("SELECT TIMESTAMPDIFF(SECOND,o.created_time,j.due_at) FROM job_task j JOIN rental_order o ON o.id=j.order_id WHERE j.id=?",f.job));
  long d=insert("INSERT INTO drone(model,price_per_day,stock,status,on_shelf) VALUES (?,1,1,1,1)",label());var dto=new OrderCreateDTO();dto.setDroneId(d);dto.setStartDate(LocalDate.now());dto.setEndDate(LocalDate.now().plusDays(1));
  UserContext.setCurrentUser(new UserContext.UserInfo(user,run,0));CommandKeys.bind(label());try{var a=orders.createOrder(dto);var b=orders.createOrder(dto);assertEquals(a.getId(),b.getId());assertEquals(1,n("SELECT COUNT(*) FROM job_task WHERE order_id=?",a.getId()));}finally{UserContext.clear();CommandKeys.clear();}
  fact("atomic_create_and_same_key",Map.of("order",f.order,"job",f.job));
 }
 @Test void actualInsertThenCheckedFaultRollsBackOrderTaskStockPaymentAndReceipt()throws Exception{
  long d=insert("INSERT INTO drone(model,price_per_day,stock,status,on_shelf) VALUES (?,1,1,1,1)",label());long tasks=n("SELECT COUNT(*) FROM job_task");hooks.fail="after_enqueue";
  assertThrows(RuntimeException.class,()->create(d));assertEquals(tasks,n("SELECT COUNT(*) FROM job_task"));assertEquals(0,n("SELECT COUNT(*) FROM rental_order WHERE drone_id=?",d));assertEquals(1,n("SELECT stock FROM drone WHERE id=?",d));assertEquals(0,n("SELECT COUNT(*) FROM drone_stock_log WHERE drone_id=?",d));
  fact("inserted_task_rollback",Map.of("device",d,"tasks_unchanged",true));
 }
 @Test void notDueCannotClaimOrCancel()throws Exception{F f=create();assertTrue(queue.claimNext(f.job).isEmpty());assertThrows(BusinessException.class,()->orders.timeoutCancel(f.order));gold(f);fact("not_due",Map.of("job",f.job));}
 @Test void dueCancellationReplaysWithoutSecondReleaseAndAuditsSystem()throws Exception{F f=create();due();assertEquals("DONE",worker.execute(claim(f)));gold(f);assertEquals(TimeoutResult.CANCELLED,orders.timeoutCancel(f.order));gold(f);long actor=system.id();assertEquals(1,n("SELECT COUNT(*) FROM command_dedup WHERE operation='TIMEOUT_CANCEL' AND resource_id=? AND actor_id=?",f.order,actor));assertEquals(0,n("SELECT COUNT(*) FROM business_event e JOIN command_dedup c ON c.id=e.command_id WHERE c.operation='TIMEOUT_CANCEL' AND c.resource_id=? AND e.actor_id<>?",f.order,actor));assertEquals(actor,n("SELECT operator_id FROM drone_stock_log WHERE related_order_id=? AND inventory_effect='RELEASE'",f.order));fact("due_system_audit_and_replay",Map.of("job",f.job,"actor",actor,"releases",1));}
 @Test void paidOrderIsNoActionDone()throws Exception{F f=create();user(()->{orders.simulatePay(f.order,"synthetic");return null;});due();assertEquals("DONE",worker.execute(claim(f)));assertEquals("NO_ACTION",text("SELECT result FROM job_task WHERE id=?",f.job));assertEquals(1,n("SELECT order_status FROM rental_order WHERE id=?",f.order));gold(f);fact("paid_no_action",Map.of("job",f.job));}
 @Test void userCancelledOrderIsNoActionDone()throws Exception{F f=create();user(()->{orders.cancelOrder(f.order,"synthetic");return null;});due();assertEquals("DONE",worker.execute(claim(f)));assertEquals("NO_ACTION",text("SELECT result FROM job_task WHERE id=?",f.job));gold(f);fact("already_cancelled_no_action",Map.of("job",f.job));}
 @Test void allOtherStatesAndLogicalDeletionRemainUnchanged()throws Exception{
  for(int state:List.of(2,3,4,6)){
   F f=create();user(()->{orders.simulatePay(f.order,"synthetic");return null;});
   UserContext.setCurrentUser(new UserContext.UserInfo(admin,run+"a",1));
   try{if(state==6)orders.refundOrder(f.order,"synthetic");else orders.shipOrder(f.order,"G3","SYNTHETIC");}finally{UserContext.clear();}
   if(state==3||state==4)user(()->{orders.confirmReceive(f.order);return null;});
   if(state==4){user(()->{orders.applyReturn(f.order);return null;});UserContext.setCurrentUser(new UserContext.UserInfo(admin,run+"a",1));try{orders.reviewReturn(f.order,true,"G3");}finally{UserContext.clear();}}
   long stock=n("SELECT stock FROM drone WHERE id=?",f.device),logs=n("SELECT COUNT(*) FROM drone_stock_log WHERE drone_id=?",f.device);
   due();assertEquals("DONE",worker.execute(claim(f)));assertEquals(state,n("SELECT order_status FROM rental_order WHERE id=?",f.order));assertEquals(stock,n("SELECT stock FROM drone WHERE id=?",f.device));assertEquals(logs,n("SELECT COUNT(*) FROM drone_stock_log WHERE drone_id=?",f.device));
  }
  F f=create();user(()->{orders.cancelOrder(f.order,"before soft deletion");return null;});jdbc.update("UPDATE rental_order SET deleted=1 WHERE id=?",f.order);due();assertEquals("DONE",worker.execute(claim(f)));assertEquals("NO_ACTION",text("SELECT result FROM job_task WHERE id=?",f.job));fact("other_states_and_soft_delete_no_action",Map.of("states",List.of(2,3,4,6),"deleted_job",f.job));
 }
 @Test void historicalOrderWithoutTaskIsNeverAutomaticallyCancelled()throws Exception{
  long d=insert("INSERT INTO drone(model,price_per_day,stock,status,on_shelf) VALUES (?,1,0,0,1)",label());
  long historic=insert("INSERT INTO rental_order(order_no,user_id,drone_id,rental_start_time,rental_end_time,rental_days,unit_price,total_amount,order_status) VALUES (?,?,?,NOW(),NOW(),1,1,1,0)",label(),user,d);
  jdbc.update("INSERT INTO drone_stock_log(drone_id,change_type,change_amount,before_stock,after_stock,related_order_id,inventory_effect,event_type) VALUES (?,2,-1,1,0,?,'ALLOCATE','FIXTURE_ALLOCATE')",d,historic);
  due();assertThrows(BusinessException.class,()->orders.timeoutCancel(historic));assertEquals(0,n("SELECT order_status FROM rental_order WHERE id=?",historic));assertEquals(0,n("SELECT stock FROM drone WHERE id=?",d));fact("no_historical_backfill",Map.of("historical_fixture_order",historic));
 }
 void race(String type)throws Exception{
  for(int round=1;round<=30;round++){
   F f=create();due();JobClaim claim=claim(f);hooks.gate=new CyclicBarrier(2);hooks.connections.clear();ExecutorService pool=Executors.newFixedThreadPool(2);
   try{
    var a=pool.submit(()->worker.execute(claim));var b=pool.submit(()->{try{return user(()->{if(type.equals("pay"))orders.simulatePay(f.order,"synthetic");else orders.cancelOrder(f.order,"synthetic");return "success";});}catch(BusinessException conflict){assertEquals(type.equals("pay")?com.drone.rental.common.ResultCode.ORDER_ALREADY_PAID.getCode():com.drone.rental.common.ResultCode.ORDER_CANNOT_CANCEL.getCode(),conflict.getCode());return "conflict";}});
    assertEquals("DONE",a.get(20,TimeUnit.SECONDS));b.get(20,TimeUnit.SECONDS);assertEquals(2,hooks.connections.size());gold(f);
    long state=n("SELECT order_status FROM rental_order WHERE id=?",f.order);assertTrue(type.equals("pay")?(state==1||state==5):state==5);fact(type+"_timeout_race",Map.of("round",round,"job",f.job,"connections",new ArrayList<>(hooks.connections),"state",state));
   }finally{hooks.gate=null;pool.shutdownNow();}
  }
 }
 @Test void paymentAndTimeoutThirtyRealTransactionRaces()throws Exception{race("pay");}
 @Test void userCancelAndTimeoutThirtyRealTransactionRaces()throws Exception{race("cancel");}
 @Test void twoWorkersSkipLockedOnlyOneClaimsSameTask()throws Exception{
  F f=create();due();var gate=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->{gate.await();return queue.claimNext(f.job);});var b=pool.submit(()->{gate.await();return queue.claimNext(f.job);});var ca=a.get(10,TimeUnit.SECONDS);var cb=b.get(10,TimeUnit.SECONDS);assertEquals(1,(ca.isPresent()?1:0)+(cb.isPresent()?1:0));assertEquals("DONE",worker.execute(ca.orElseGet(cb::orElseThrow)));gold(f);fact("two_worker_claim",Map.of("job",f.job,"successful_claims",1));}finally{pool.shutdownNow();}
 }
 @Test void skipLockedActuallySkipsAnInFlightMysqlRowLock()throws Exception{
  F f=create();due();
  try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()){
   connection.setAutoCommit(false);
   try(var st=connection.prepareStatement("SELECT id FROM job_task WHERE id=? FOR UPDATE")){st.setLong(1,f.job);try(var row=st.executeQuery()){assertTrue(row.next());}}
   long start=System.nanoTime();assertTrue(queue.claimNext(f.job).isEmpty());assertTrue(System.nanoTime()-start<java.util.concurrent.TimeUnit.SECONDS.toNanos(2),"SKIP LOCKED must not wait for the held row");connection.rollback();
  }
  assertEquals("DONE",worker.execute(claim(f)));gold(f);fact("real_locked_row_skip",Map.of("job",f.job,"skipped_inflight_lock",true));
 }
 @Test void oldWorkerAfterBusinessCommitCannotOverwriteNewClaimDone()throws Exception{
  F f=create();due();JobClaim old=claim(f);var committed=new CountDownLatch(1);var release=new CountDownLatch(1);
  hooks.afterBusiness=c->{if(c.token().equals(old.token())){assertFalse(TransactionSynchronizationManager.isActualTransactionActive());committed.countDown();try{assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}}};
  var pool=Executors.newSingleThreadExecutor();
  try{var stale=pool.submit(()->worker.execute(old));assertTrue(committed.await(15,TimeUnit.SECONDS));gold(f);clock.advance(11);JobClaim newer=claim(f);assertEquals("DONE",worker.execute(newer));release.countDown();assertEquals("STALE",stale.get(15,TimeUnit.SECONDS));assertEquals(newer.token(),text("SELECT claim_token FROM job_task WHERE id=?",f.job));assertEquals("DONE",text("SELECT status FROM job_task WHERE id=?",f.job));gold(f);fact("committed_old_worker_fenced_by_new_done",Map.of("job",f.job,"releases",1,"old_result","STALE"));}finally{release.countDown();hooks.afterBusiness=null;pool.shutdownNow();}
 }
 @Test void expiredClaimFencesOldWorkerAndCanBeRecovered()throws Exception{F f=create();due();JobClaim old=claim(f);clock.advance(11);JobClaim current=claim(f);assertNotEquals(old.token(),current.token());assertFalse(queue.done(old,TimeoutResult.CANCELLED));assertFalse(queue.failed(old,new IllegalStateException()));assertEquals("STALE",worker.execute(old));assertEquals("DONE",worker.execute(current));gold(f);fact("lease_expiry_fencing",Map.of("job",f.job,"attempts",current.attempts(),"old_worker_updates",0));}
 @Test void retryBackoffAndFinalFailureDoNotPersistSecretOrReleaseStock()throws Exception{F f=create();due();hooks.fail="before_business";for(int attempt=1;attempt<=3;attempt++){assertEquals("RECORDED_FAILURE",worker.execute(claim(f)));if(attempt<3){assertEquals("PENDING",text("SELECT status FROM job_task WHERE id=?",f.job));assertTrue(queue.claimNext(f.job).isEmpty());clock.advance((1L<<attempt)+1);}}assertEquals("FAILED",text("SELECT status FROM job_task WHERE id=?",f.job));assertEquals("COMMAND_FAILURE",text("SELECT last_error FROM job_task WHERE id=?",f.job));assertTrue(queue.claimNext(f.job).isEmpty());gold(f);fact("bounded_retry_failed",Map.of("job",f.job,"attempts",3,"error","COMMAND_FAILURE"));}
 @Test void repeatedCrashAfterClaimEventuallyFails()throws Exception{F f=create();due();for(int i=0;i<3;i++){claim(f);clock.advance(11);}assertTrue(queue.claimNext(f.job).isEmpty());assertEquals("FAILED",text("SELECT status FROM job_task WHERE id=?",f.job));gold(f);fact("crash_claim_attempt_limit",Map.of("job",f.job));}
 @Test void committedBusinessBeforeDoneRecoversWithOriginalKey()throws Exception{F f=create();due();hooks.fail="after_business";assertEquals("RECORDED_FAILURE",worker.execute(claim(f)));gold(f);hooks.fail=null;clock.advance(3);assertEquals("DONE",worker.execute(claim(f)));gold(f);assertEquals(1,n("SELECT COUNT(*) FROM command_dedup WHERE operation='TIMEOUT_CANCEL' AND resource_id=?",f.order));fact("business_committed_done_failed",Map.of("job",f.job,"release_count",1));}
 @Test void systemActorCannotBeUsedFromUserOrHttpAndCannotLogin()throws Exception{assertEquals("SYSTEM",text("SELECT principal_kind FROM user WHERE id=?",system.id()));assertEquals(0,n("SELECT status FROM user WHERE id=?",system.id()));assertThrows(SecurityException.class,()->user(()->orders.timeoutCancel(1L)));String sysToken=jwt.generateToken(system.id(),"__leaseguard_timeout__",0,"!SYSTEM_NON_LOGIN!");
  var client=java.net.http.HttpClient.newHttpClient();
  var denied=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/user/info")).header("Authorization","Bearer "+sysToken).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(403,denied.statusCode());
  var login=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/auth/login")).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"username\":\"__leaseguard_timeout__\",\"password\":\"!SYSTEM_NON_LOGIN!\"}")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(401,login.statusCode());
  assertEquals(0,app.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).size());assertTrue(app.getBeanDefinitionNames().length>0);assertFalse(Arrays.stream(app.getBeanDefinitionNames()).anyMatch(n->n.toLowerCase().contains("langchain")));fact("system_identity_and_ai_off",Map.of("http_context_impersonation",false,"scheduler_enabled",false,"ai_beans",0));}
 int child(F f,boolean crash,String name)throws Exception{
  Path log=Path.of(System.getenv("LEASEGUARD_EVIDENCE_DIR"),name+".log");
  var p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-Xmx384m","-cp",System.getProperty("java.class.path"),JobRestartProbe.class.getName(),"--server.port=0","--leaseguard.jobs.enabled=false","--spring.datasource.hikari.maximum-pool-size=2","--spring.datasource.hikari.minimum-idle=1","--probe.task="+f.job,"--probe.now="+clock.instant(),"--probe.crash="+crash).redirectErrorStream(true).redirectOutput(log.toFile()).start();
  if(!p.waitFor(60,TimeUnit.SECONDS)){p.destroyForcibly();fail("Restart probe deadline; see "+name);}return p.exitValue();
 }
 @Test void realJvmHaltAfterCommitAndSecondProcessRestartRecover()throws Exception{F f=create();due();assertEquals(77,child(f,true,"restart-crashed"));assertEquals("CLAIMED",text("SELECT status FROM job_task WHERE id=?",f.job));gold(f);clock.advance(31);assertEquals(0,child(f,false,"restart-recovered"));assertEquals("DONE",text("SELECT status FROM job_task WHERE id=?",f.job));gold(f);fact("two_real_jvm_restart",Map.of("job",f.job,"first_process_exit",77,"second_process_exit",0,"release_count",1));}
}
