package com.drone.rental.jobs;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import com.drone.rental.security.UserContext;
/** Explicit non-login actor, never installs or forges UserContext. */
@Component
public class SystemActor {
 private final JdbcTemplate jdbc;
 public SystemActor(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public long id(){
  if(RequestContextHolder.getRequestAttributes()!=null || UserContext.getCurrentUser()!=null)throw new SecurityException("System command cannot run in a user/request context");
  return jdbc.queryForObject("SELECT id FROM user WHERE username='__leaseguard_timeout__' AND principal_kind='SYSTEM' AND status=0 AND role=0 AND deleted=0 AND password='!SYSTEM_NON_LOGIN!'",Long.class);
 }
}
