package com.drone.rental;
import com.drone.rental.security.JwtUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import java.sql.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="leaseguard.jobs.enabled=false")
@Import(SqlMeasurement.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ListSqlG3IT {
 @Autowired JdbcTemplate jdbc;@Autowired JwtUtil jwt;@Autowired ObjectMapper json;@LocalServerPort int port;
 final Path evidence=Path.of(System.getenv("LEASEGUARD_EVIDENCE_DIR"));
 long user,admin,device,other;String run;
 long insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},key);return Objects.requireNonNull(key.getKey()).longValue();}
 void fixtures()throws Exception{
  assertEquals("leaseguard_test",jdbc.queryForObject("SELECT DATABASE()",String.class));Path file=evidence.resolve("sql-fixture.json");
  if(Files.exists(file)){var f=json.readTree(Files.readString(file));user=f.get("user").asLong();admin=f.get("admin").asLong();device=f.get("device").asLong();other=f.get("other").asLong();run=f.get("run").asText();assertEquals(run+"u",jdbc.queryForObject("SELECT username FROM user WHERE id=?",String.class,user));return;}
  run="G3SQL"+UUID.randomUUID().toString().replace("-","").substring(0,8);
  user=insert("INSERT INTO user(username,password,nickname,role) VALUES (?,'unusable-sql-test','SQL synthetic',0)",run+"u");
  admin=insert("INSERT INTO user(username,password,role) VALUES (?,'unusable-sql-test',1)",run+"a");other=insert("INSERT INTO user(username,password,role) VALUES (?,'unusable-sql-test',0)",run+"o");
  device=insert("INSERT INTO drone(model,brand,price_per_day,stock,status,on_shelf) VALUES (?,'SQL brand',12.50,1,1,1)",run);
  for(int i=0;i<100;i++){
   var created=LocalDateTime.of(2026,1,1,0,0).plusSeconds(i);
   long order=insert("INSERT INTO rental_order(order_no,user_id,drone_id,rental_start_time,rental_end_time,rental_days,unit_price,total_amount,order_status,created_time) VALUES (?,?,?,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),2,12.50,25,3,?)",run+i,user,device,created);
   long fault=insert("INSERT INTO fault_report(report_no,user_id,drone_id,order_id,fault_type,fault_description,audit_status) VALUES (?,?,?,?,?,'SQL synthetic',1)",run+i,user,device,order,"SQL fault");
   insert("INSERT INTO maintenance_ticket(ticket_no,fault_report_id,user_id,drone_id,maintenance_type,status,created_time) VALUES (?,?,?,?,'SQL type',2,?)",run+i,fault,user,device,created);
   if(i%2==0)jdbc.update("INSERT INTO return_request(order_id,user_id,status,submitted_at) VALUES (?,?,0,NOW())",order,user);
  }
  Files.writeString(file,json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("run",run,"user",user,"admin",admin,"device",device,"other",other)));
 }
 String token(long id,int role){return jwt.generateToken(id,run+(role==1?"a":id==user?"u":"o"),role,"unusable-sql-test");}
 HttpResponse<String> get(String path,String token)throws Exception{return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Authorization","Bearer "+token).header("X-G3-Measure","true").GET().build(),HttpResponse.BodyHandlers.ofString());}
 @Test void measureActualHttpSqlBeforeAndAfterOnIdenticalSyntheticRows()throws Exception{
  fixtures();String phase=System.getenv().getOrDefault("LEASEGUARD_SQL_PHASE","after");List<Map<String,Object>> results=new ArrayList<>();
  for(String type:List.of("orders","repairs"))for(int size:List.of(10,50,100)){
   String url=type.equals("orders")?"/admin/order/list?page=1&pageSize="+size+"&orderNo="+run:"/admin/maintenance/ticket/list?page=1&pageSize="+size+"&droneId="+device;
   for(int warm=0;warm<2;warm++)assertEquals(200,get(url,token(admin,1)).statusCode());
   List<Long> elapsed=new ArrayList<>(),db=new ArrayList<>();int count=-1;String payload=null;
   for(int round=0;round<5;round++){
    long start=System.nanoTime();var r=get(url,token(admin,1));elapsed.add(System.nanoTime()-start);assertEquals(200,r.statusCode());
    var data=json.readTree(r.body()).get("data");assertEquals(100,data.get("total").asInt());assertEquals(size,data.get("records").size());
    payload=data.toString();count=Integer.parseInt(r.headers().firstValue("X-G3-SQL-Count").orElseThrow());db.add(Long.parseLong(r.headers().firstValue("X-G3-DB-Nanos").orElseThrow()));
    for(var row:data.get("records")){assertEquals(run,row.get("droneModel").asText());if(type.equals("orders")){assertEquals("SQL synthetic",row.get("username").asText());assertEquals(0,new java.math.BigDecimal("25").compareTo(row.get("totalAmount").decimalValue()));}else{assertEquals("SQL fault",row.get("faultType").asText());assertEquals(run+"u",row.get("userName").asText());}}
   }
   if(phase.equals("before"))assertTrue(count>=size*2);else assertTrue(count<=8,"SQL still scales per row: "+count);
   Collections.sort(elapsed);Collections.sort(db);
   Path payloadFile=evidence.resolve("sql-"+type+"-"+size+"-before-payload.json");
   if(phase.equals("before"))Files.writeString(payloadFile,payload);else {
    if(Files.exists(payloadFile)){var expected=json.readTree(Files.readString(payloadFile));if(type.equals("orders"))for(var row:expected.get("records"))((com.fasterxml.jackson.databind.node.ObjectNode)row).put("orderStatusDesc",com.drone.rental.service.OrderLabels.label(row.get("orderStatus").asInt()));assertEquals(expected,json.readTree(payload),"Contract/rows changed beyond approved status labels");}
    else if("true".equals(System.getenv("GITHUB_ACTIONS"))||"generated".equals(System.getenv("LEASEGUARD_SQL_FIXTURE_MODE"))){var expectedIds=jdbc.queryForList(type.equals("orders")?"SELECT id FROM rental_order WHERE order_no LIKE ? AND deleted=0 ORDER BY created_time DESC,id DESC LIMIT ?":"SELECT id FROM maintenance_ticket WHERE drone_id=? AND deleted=0 ORDER BY created_time DESC,id DESC LIMIT ?",Long.class,type.equals("orders")?"%"+run+"%":device,size);var got=new ArrayList<Long>();for(var row:json.readTree(payload).get("records"))got.add(row.get("id").asLong());assertEquals(expectedIds,got);}
    else fail("Historical SQL payload missing: prepare the accepted synthetic baseline, do not silently skip it");
   }
   var m=new LinkedHashMap<String,Object>();m.put("type",type);m.put("size",size);m.put("sql_count",count);m.put("jdbc_execute_median_ms",db.get(2)/1e6);m.put("http_median_ms",elapsed.get(2)/1e6);m.put("http_samples_ms",elapsed.stream().map(n->n/1e6).toList());m.put("sql_shapes",SqlMeasurement.LAST.get().sql);m.put("same_result",true);results.add(m);
   Files.writeString(evidence.resolve("list-sql-"+phase+".json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("phase",phase,"source_sha",System.getenv("LEASEGUARD_TEST_SHA"),"measure","real HTTP including security JDBC; 2 warmups + 5 samples; no load claim","fixture_run",run,"results",results)));
  }
  assertEquals(403,get("/admin/order/list",token(user,0)).statusCode());
  var own=json.readTree(get("/user/orders?pageSize=100",token(user,0)).body()).get("data");assertEquals(100,own.get("total").asInt());
  var empty=json.readTree(get("/user/orders",token(other,0)).body()).get("data");assertEquals(0,empty.get("total").asInt());
 }
}
