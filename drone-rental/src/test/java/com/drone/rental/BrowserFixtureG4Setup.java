package com.drone.rental;
import com.drone.rental.command.*;
import com.drone.rental.security.*;
import com.drone.rental.service.*;
import com.drone.rental.dto.*;
import com.drone.rental.jobs.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.sql.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
/** Explicit local/CI fixture command; never an application endpoint or a default test. */
public class BrowserFixtureG4Setup {
 static class TestClock extends Clock {
  Instant time=Instant.now().minusSeconds(120);public ZoneId getZone(){return ZoneId.of("Asia/Shanghai");}public Clock withZone(ZoneId z){return this;}public Instant instant(){return time;}
 }
 @TestConfiguration static class Config {@Bean @Primary TestClock fixtureClock(){return new TestClock();}}
 JdbcTemplate db;OrderService orders;FaultReportService faults;MaintenanceTicketService repairs;String run="g4_"+UUID.randomUUID().toString().substring(0,8);long admin,user,a,b;
 long insert(String sql,Object...args){var keys=new GeneratedKeyHolder();db.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
 long n(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
 <T>T as(long id,java.util.concurrent.Callable<T> action)throws Exception{UserContext.setCurrentUser(new UserContext.UserInfo(id,"synthetic",(int)n("SELECT role FROM user WHERE id=?",id)));CommandKeys.bind("g4-fixture-"+UUID.randomUUID());try{return action.call();}finally{UserContext.clear();CommandKeys.clear();}}
 long device(String tag,int stock){return insert("INSERT INTO drone(model,brand,type,price_per_day,stock,status,on_shelf) VALUES (?,'G4 synthetic','巡检',12.50,?,1,1)",run+"_"+tag,stock);}
 Map<String,Object> order(long d,boolean paid)throws Exception{return as(user,()->{var dto=new OrderCreateDTO();dto.setDroneId(d);dto.setStartDate(LocalDate.now().plusDays(1));dto.setEndDate(LocalDate.now().plusDays(2));var o=orders.createOrder(dto);if(paid)orders.simulatePay(o.getId(),"G4 synthetic");return new LinkedHashMap<>(Map.of("id",o.getId().toString(),"number",o.getOrderNo(),"device",String.valueOf(d)));});}
 Map<String,Object> fault(String tag,boolean audit)throws Exception{long d=device(tag,2);var o=order(d,true);long id=Long.parseLong((String)o.get("id"));as(admin,()->{orders.shipOrder(id,"G4","synthetic");return null;});as(user,()->{orders.confirmReceive(id);var f=new FaultReportDTO();f.setDroneId(d);f.setOrderId(id);f.setFaultType("G4 browser");f.setFaultDescription(run+"_"+tag);faults.reportFault(f);return null;});long fid=n("SELECT id FROM fault_report WHERE order_id=?",id);o.put("fault",String.valueOf(fid));o.put("faultNumber",db.queryForObject("SELECT report_no FROM fault_report WHERE id=?",String.class,fid));if(audit){as(admin,()->{var dto=new AuditDTO();dto.setAuditStatus(1);faults.auditFaultReport(fid,dto);long ticket=n("SELECT id FROM maintenance_ticket WHERE fault_report_id=?",fid);repairs.assign(ticket,b,0);return null;});o.put("ticket",String.valueOf(n("SELECT id FROM maintenance_ticket WHERE fault_report_id=?",fid)));}return o;}
 public static void main(String[] args)throws Exception{
  Path target=Path.of(System.getenv("LEASEGUARD_EVIDENCE_DIR")).resolve("browser/fixture.json");if(Files.exists(target))throw new IllegalStateException("Never overwrite a browser fixture");
  String pw=Objects.requireNonNull(System.getenv("LEASEGUARD_E2E_PASSWORD"));
  try(var c=new SpringApplicationBuilder(DroneRentalApplication.class,Config.class).run("--server.port=0","--leaseguard.jobs.enabled=false","--leaseguard.jobs.timeout-seconds=1","--leaseguard.jobs.max-attempts=1")){
   var f=new BrowserFixtureG4Setup();f.db=c.getBean(JdbcTemplate.class);if(!"leaseguard_dev".equals(f.db.queryForObject("SELECT DATABASE()",String.class)))throw new IllegalStateException("Only dedicated dev catalog");f.orders=c.getBean(OrderService.class);f.faults=c.getBean(FaultReportService.class);f.repairs=c.getBean(MaintenanceTicketService.class);String hash=c.getBean(PasswordEncoder.class).encode(pw);var result=new LinkedHashMap<String,Object>();result.put("run",f.run);
   for(var item:Map.of("admin",1,"user",0,"techA",2,"techB",2).entrySet()){String name=f.run+"_"+item.getKey();long id=f.insert("INSERT INTO user(username,password,role,status,nickname) VALUES (?,?,?,1,?)",name,hash,item.getValue(),name);result.put(item.getKey(),name);result.put(item.getKey()+"Id",String.valueOf(id));switch(item.getKey()){case "admin"->f.admin=id;case "user"->f.user=id;case "techA"->f.a=id;case "techB"->f.b=id;}}
   f.db.update("INSERT INTO user_qualification(user_id,certificate_no,valid_start_date,valid_end_date,audit_status) VALUES (?,?,'2020-01-01','2099-12-31',1)",f.user,f.run);
   result.put("returnDevice",String.valueOf(f.device("RETURN",1)));result.put("repair",f.fault("REPAIR_A",false));result.put("otherRepair",f.fault("REPAIR_B",true));
   result.put("scheduled",f.order(f.device("SCHEDULED",1),false));var failed=f.order(f.device("FAILED",1),false);long failedId=Long.parseLong((String)failed.get("id"));var clock=c.getBean(TestClock.class);clock.time=clock.time.plusSeconds(2);var queue=c.getBean(JobQueue.class);long task=f.n("SELECT id FROM job_task WHERE order_id=?",failedId);var claim=queue.claimNext(task).orElseThrow();if(!queue.failed(claim,new IllegalStateException("Synthetic controlled failure")))throw new IllegalStateException("Fixture failure not recorded");failed.put("task",String.valueOf(task));result.put("failed",failed);
   result.put("scheduledInitial",f.db.queryForMap("SELECT status,attempts FROM job_task WHERE order_id=?",Long.parseLong((String)((Map<?,?>)result.get("scheduled")).get("id"))));result.put("source_sha",System.getenv("LEASEGUARD_TEST_SHA"));Files.createDirectories(target.getParent());new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(target.toFile(),result);
  }
 }
}
