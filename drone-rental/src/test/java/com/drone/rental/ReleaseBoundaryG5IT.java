package com.drone.rental;
import com.drone.rental.security.JwtUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.net.*;import java.net.http.*;import java.sql.*;import java.util.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="leaseguard.jobs.enabled=false")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ReleaseBoundaryG5IT {
 @Autowired JdbcTemplate jdbc;@Autowired JwtUtil jwt;@LocalServerPort int port;
 int request(String path,boolean post,String token)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));if(token!=null)b.header("Authorization","Bearer "+token);
  return HttpClient.newHttpClient().send(post?b.POST(HttpRequest.BodyPublishers.noBody()).build():b.GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();
 }
 @Test void unauthenticatedFilesCannotBeReadOrUploaded()throws Exception{assertEquals(401,request("/uploads/synthetic.html",false,null));assertEquals(401,request("/common/upload",true,null));}
 @Test void allInteractiveRolesAreDeniedUntilAnOwnershipSafeFileModelExists()throws Exception{
  assertEquals("leaseguard_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
  for(int role:List.of(0,1,2)){String name="g5f_"+UUID.randomUUID().toString().substring(0,8);var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement("INSERT INTO user(username,password,role) VALUES (?,'unusable-release-test',?)",Statement.RETURN_GENERATED_KEYS);s.setString(1,name);s.setInt(2,role);return s;},key);long id=Objects.requireNonNull(key.getKey()).longValue();String token=jwt.generateToken(id,name,role,"unusable-release-test");assertEquals(403,request("/uploads/synthetic.html",false,token));assertEquals(403,request("/common/upload",true,token));}
 }
}
