package com.drone.rental.security;
import com.drone.rental.common.Result;
import com.drone.rental.entity.User;
import com.drone.rental.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(10); }
 @Bean org.springframework.security.core.userdetails.UserDetailsService noDefaultUsers(){return name->{throw new org.springframework.security.core.userdetails.UsernameNotFoundException("JWT only");};}
 static void error(HttpServletResponse r,int status,ObjectMapper json) throws IOException {
  r.setStatus(status);r.setContentType("application/json;charset=UTF-8");json.writeValue(r.getOutputStream(),Result.error(status,status==401?"请重新登录":"没有操作权限"));
 }
 @Bean SecurityFilterChain chain(HttpSecurity http,JwtUtil jwt,UserMapper users,ObjectMapper json) throws Exception {
  http.csrf(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
   .exceptionHandling(c->c.authenticationEntryPoint((q,r,e)->error(r,401,json)).accessDeniedHandler((q,r,e)->error(r,403,json)))
   .authorizeHttpRequests(c->c
    // No ownership-safe file model exists yet. Public candidate keeps both surfaces closed.
    .requestMatchers("/common/upload","/uploads/**").denyAll()
    .requestMatchers("/auth/login","/auth/admin/login","/auth/register","/error","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
    .requestMatchers(HttpMethod.GET,"/drone/list","/drone/detail/**","/drone/brands","/drone/types").permitAll()
    .requestMatchers("/admin/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.POST,"/workbench/tickets/*/start","/workbench/tickets/*/complete").hasRole("TECHNICIAN")
    .requestMatchers("/workbench/**").hasAnyRole("ADMIN","TECHNICIAN")
    .requestMatchers("/user/info").authenticated()
    .requestMatchers("/order/**","/user/**","/fault/**").hasAnyRole("USER","ADMIN")
    .anyRequest().authenticated())
   .addFilterBefore(new OncePerRequestFilter(){
    @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain) throws ServletException,IOException {
     try {
      String header=q.getHeader("Authorization");
      if(header!=null){
       Claims claims=header.startsWith("Bearer ")?jwt.getClaimsFromToken(header.substring(7)):null;
       if(claims==null || !(claims.get("userId") instanceof Number)){error(r,401,json);return;}
       User u=users.selectById(((Number)claims.get("userId")).longValue());
       if(u==null || !JwtUtil.stamp(u.getPassword()).equals(claims.get("credentialStamp"))){error(r,401,json);return;}
       if("SYSTEM".equals(u.getPrincipalKind()) || !Integer.valueOf(1).equals(u.getStatus())){error(r,403,json);return;}
       var auth=new UsernamePasswordAuthenticationToken(u.getId(),null,List.of(new SimpleGrantedAuthority(Roles.authority(u.getRole()))));
       SecurityContextHolder.getContext().setAuthentication(auth);
       UserContext.setCurrentUser(new UserContext.UserInfo(u.getId(),u.getUsername(),u.getRole()));
      }
      chain.doFilter(q,r);
     } finally { UserContext.clear();SecurityContextHolder.clearContext(); }
    }
   },UsernamePasswordAuthenticationFilter.class);
  return http.build();
 }
}
