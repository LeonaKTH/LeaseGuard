package com.drone.rental;
import com.drone.rental.security.JwtUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import java.sql.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="leaseguard.jobs.enabled=false")
@org.springframework.context.annotation.Import(SqlMeasurement.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListContractsG3IT {
 @Autowired JdbcTemplate jdbc;@Autowired JwtUtil jwt;@Autowired ObjectMapper json;@LocalServerPort int port;
 long owner,admin,stranger,device,region;String run="G3L"+UUID.randomUUID().toString().replace("-","").substring(0,10);final List<Long> orderIds=new ArrayList<>();
 long insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},key);return Objects.requireNonNull(key.getKey()).longValue();}
 @BeforeAll void fixture(){
  assertEquals("leaseguard_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
  owner=insert("INSERT INTO user(username,password,nickname,phone) VALUES (?,'unusable-contract-test','contract synthetic',?)",run+"u",run+"phone");admin=insert("INSERT INTO user(username,password,role) VALUES (?,'unusable-contract-test',1)",run+"a");stranger=insert("INSERT INTO user(username,password) VALUES (?,'unusable-contract-test')",run+"s");device=insert("INSERT INTO drone(model,brand,price_per_day,stock,status,on_shelf) VALUES (?,'Contract brand',1,0,0,1)",run);region=insert("INSERT INTO airspace_record(user_id,region_name) VALUES (?,?)",owner,run+" region");
  for(int i=0;i<12;i++){
   long order=insert("INSERT INTO rental_order(order_no,user_id,drone_id,airspace_record_id,rental_start_time,rental_end_time,rental_days,unit_price,total_amount,order_status,created_time) VALUES (?,?,?,?,NOW(),NOW(),1,1,1,3,'2026-01-02 00:00:00')",run+i,owner,device,i%2==0?region:null);orderIds.add(order);
   jdbc.update("INSERT INTO drone_stock_log(drone_id,change_type,change_amount,before_stock,after_stock,related_order_id,inventory_effect) VALUES (?,2,-1,?,?,?,'ALLOCATE')",device,12-i,11-i,order);
   long fault=insert("INSERT INTO fault_report(report_no,user_id,drone_id,order_id,fault_type,fault_description,audit_status) VALUES (?,?,?,?,'contract fault','synthetic',1)",run+i,owner,device,order);
   insert("INSERT INTO maintenance_ticket(ticket_no,user_id,drone_id,fault_report_id,maintenance_type,status,created_time) VALUES (?,?,?,?,'contract type',2,'2026-01-02 00:00:00')",run+i,owner,device,fault);
  }
 }
 String token(long actor){return jwt.generateToken(actor,run,actor==admin?1:0,"unusable-contract-test");}
 HttpResponse<String> get(String path,long actor)throws Exception{return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Authorization","Bearer "+token(actor)).header("X-G3-Measure","true").GET().build(),HttpResponse.BodyHandlers.ofString());}
 JsonNode data(String path,long actor)throws Exception{var r=get(path,actor);assertEquals(200,r.statusCode(),path);return json.readTree(r.body()).get("data");}
 String orderPath(int page,int size){return "/admin/order/list?orderNo="+run+"&page="+page+"&pageSize="+size;}
 String repairPath(int page,int size){return "/admin/maintenance/ticket/list?droneId="+device+"&page="+page+"&pageSize="+size;}
 @Test void boundedPagesRejectInvalidSizeAndPage()throws Exception{for(String path:List.of(orderPath(0,10),orderPath(1,0),orderPath(1,101),repairPath(0,10),repairPath(1,101),"/user/orders?pageNum=1&pageSize=101"))assertEquals(400,get(path,admin).statusCode());}
 @Test void equalTimestampPaginationUsesStableDescendingIdsWithoutDuplicates()throws Exception{
  for(boolean order:List.of(true,false)){List<Long> ids=new ArrayList<>();for(int p=1;p<=3;p++){var page=data(order?orderPath(p,4):repairPath(p,4),admin);assertEquals(12,page.get("total").asInt());for(var row:page.get("records"))ids.add(row.get("id").asLong());}assertEquals(12,new HashSet<>(ids).size());var sorted=new ArrayList<>(ids);sorted.sort(Comparator.reverseOrder());assertEquals(sorted,ids);assertEquals(data(order?orderPath(1,4):repairPath(1,4),admin),data(order?orderPath(1,4):repairPath(1,4),admin));}
 }
 @Test void repeatedAssociationsAndOptionalRegionPreserveFullContract()throws Exception{
  var measured=get(orderPath(1,100),admin);assertEquals(7,Integer.parseInt(measured.headers().firstValue("X-G3-SQL-Count").orElseThrow()));var page=json.readTree(measured.body()).get("data");assertEquals(12,page.get("records").size());int present=0,absent=0;
  for(var o:page.get("records")){assertEquals("contract synthetic",o.get("username").asText());assertEquals(run,o.get("droneModel").asText());assertEquals(3,o.get("orderStatus").asInt());if(o.get("regionName").isNull())absent++;else{present++;assertEquals(run+" region",o.get("regionName").asText());}assertTrue(o.has("returnRequest"));}
  assertEquals(6,present);assertEquals(6,absent);
  for(var t:data(repairPath(1,100),admin).get("records")){assertEquals(run+"u",t.get("userName").asText());assertEquals("Contract brand",t.get("droneBrand").asText());assertEquals("contract fault",t.get("faultType").asText());assertEquals(2,t.get("status").asInt());assertTrue(t.has("acceptedAt"));}
 }
 @Test void permissionsAndEmptyPagesRemainServerAuthoritative()throws Exception{assertEquals(403,get(orderPath(1,10),owner).statusCode());assertEquals(403,get(repairPath(1,10),owner).statusCode());assertEquals(404,get("/order/"+orderIds.getFirst(),stranger).statusCode());assertEquals(12,data("/user/orders?pageSize=100",owner).get("total").asInt());var empty=data("/user/orders",stranger);assertEquals(0,empty.get("total").asInt());assertEquals(0,empty.get("records").size());}
 @Test void modelPhoneDateTypeAndStatusFiltersStayInDatabase()throws Exception{assertEquals(12,data(orderPath(1,100)+"&userPhone="+run+"phone&orderStatus=3&startDate=2026-01-02&endDate=2026-01-02",admin).get("total").asInt());assertEquals(12,data(repairPath(1,100)+"&droneModel="+run+"&type=contract%20type&status=2",admin).get("total").asInt());assertEquals(0,data(orderPath(1,10)+"&userPhone=NEVER_MATCH_G3",admin).get("total").asInt());}
 @Test void softDeletedRelatedDataAndMissingOptionalRegionNeverLeakOrThrow500()throws Exception{
  long ghost=insert("INSERT INTO user(username,password,deleted) VALUES (?,'unusable-contract-test',1)",run+"ghost");long d=insert("INSERT INTO drone(model,price_per_day,stock,status,deleted) VALUES (?,1,1,1,1)",run+"ghost");
  String label="G3M"+UUID.randomUUID().toString().replace("-","").substring(0,12);long oid=insert("INSERT INTO rental_order(order_no,user_id,drone_id,airspace_record_id,rental_start_time,rental_end_time,rental_days,unit_price,total_amount,order_status,inventory_held) VALUES (?,?,?,999999999,NOW(),NOW(),1,1,1,5,0)",label,ghost,d);
  var rows=data("/admin/order/list?orderNo="+label,admin).get("records");assertEquals(1,rows.size());var row=rows.get(0);assertEquals(oid,row.get("id").asLong());assertTrue(row.get("username").isNull());assertTrue(row.get("droneModel").isNull());assertTrue(row.get("regionName").isNull());
 }
}
