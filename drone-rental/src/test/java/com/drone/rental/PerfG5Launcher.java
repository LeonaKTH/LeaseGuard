package com.drone.rental;
import com.drone.rental.service.ListAssembler;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import java.util.*;import java.sql.*;
/** Opt-in, test-classpath only instrumentation; no endpoint, credentials or production artifact. */
public class PerfG5Launcher {
 static final ThreadLocal<Long> assembly=ThreadLocal.withInitial(()->0L);
 static final ThreadLocal<Long> checkout=ThreadLocal.withInitial(()->0L);
 static volatile javax.sql.DataSource datasource;
 @TestConfiguration @Import(SqlMeasurement.class)
 static class Config {
  @Bean @Primary FlywayMigrationStrategy perfMigration(){return f->{try(var c=f.getConfiguration().getDataSource().getConnection()){if(!"leaseguard_perf".equals(c.getCatalog())||!c.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:13308/leaseguard_perf?"))throw new IllegalStateException("Perf catalog and local instance only");}catch(SQLException ex){throw new IllegalStateException(ex);}f.migrate();};}
  @Bean static BeanPostProcessor assemblerTiming(){return new BeanPostProcessor(){public Object postProcessAfterInitialization(Object bean,String name){
   if(bean instanceof javax.sql.DataSource d){datasource=d;return new org.springframework.jdbc.datasource.AbstractDataSource(){
    public Connection getConnection()throws SQLException{long t=System.nanoTime();try{return d.getConnection();}finally{checkout.set(checkout.get()+System.nanoTime()-t);}}
    public Connection getConnection(String u,String p)throws SQLException{long t=System.nanoTime();try{return d.getConnection(u,p);}finally{checkout.set(checkout.get()+System.nanoTime()-t);}}
    public <T>T unwrap(Class<T> type)throws SQLException{return d.unwrap(type);}public boolean isWrapperFor(Class<?> type)throws SQLException{return d.isWrapperFor(type);}
   };}
   if(!(bean instanceof ListAssembler))return bean;var p=new ProxyFactory(bean);p.setProxyTargetClass(true);p.addAdvice((MethodInterceptor)i->{long start=System.nanoTime();try{return i.proceed();}finally{assembly.set(assembly.get()+System.nanoTime()-start);}});return p.getProxy();
  }};}
  @Bean FilterRegistrationBean<OncePerRequestFilter> perfHeaders(){var f=new FilterRegistrationBean<OncePerRequestFilter>();f.setOrder(Integer.MIN_VALUE+1);f.setFilter(new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain c)throws ServletException,java.io.IOException{
   assembly.set(0L);checkout.set(0L);try{c.doFilter(q,r);}finally{
    r.setHeader("X-G5-Assembly-Nanos",String.valueOf(assembly.get()));assembly.remove();
    r.setHeader("X-G5-Checkout-Nanos",String.valueOf(checkout.get()));checkout.remove();
    try{var h=datasource.unwrap(HikariDataSource.class).getHikariPoolMXBean();if(h!=null){r.setHeader("X-G5-Pool-Awaiting",String.valueOf(h.getThreadsAwaitingConnection()));r.setHeader("X-G5-Pool-Active",String.valueOf(h.getActiveConnections()));}}catch(SQLException e){throw new IllegalStateException(e);}
   }
  }});return f;}
 }
 public static void main(String[] args)throws Exception{
  if(!System.getenv("LEASEGUARD_DB_URL").startsWith("jdbc:mysql://127.0.0.1:13308/leaseguard_perf?"))throw new IllegalStateException("Explicit isolated perf catalog required");
  var context=new SpringApplicationBuilder(DroneRentalApplication.class,Config.class).run(args);
  if(Arrays.asList(args).contains("--prepare-jobs")){
   try{
    var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);var keys=new org.springframework.jdbc.support.GeneratedKeyHolder();String model="SYNTHETIC_G5_SERVICE_JOBS_"+UUID.randomUUID().toString().substring(0,8);
    jdbc.update(c->{var s=c.prepareStatement("INSERT INTO drone(model,brand,price_per_day,stock,status,on_shelf) VALUES (?,'SYNTHETIC',12.50,450,1,1)",Statement.RETURN_GENERATED_KEYS);s.setString(1,model);return s;},keys);long device=Objects.requireNonNull(keys.getKey()).longValue();
    var orders=context.getBean(com.drone.rental.service.OrderService.class);var ids=new ArrayList<Long>();
    com.drone.rental.security.UserContext.setCurrentUser(new com.drone.rental.security.UserContext.UserInfo(1001L,"G5 synthetic",0));
    try{for(int i=0;i<400;i++){com.drone.rental.command.CommandKeys.bind("g5-worker-"+UUID.randomUUID());var dto=new com.drone.rental.dto.OrderCreateDTO();dto.setDroneId(device);dto.setStartDate(java.time.LocalDate.now().plusDays(1));dto.setEndDate(java.time.LocalDate.now().plusDays(2));ids.add(orders.createOrder(dto).getId());}}
    finally{com.drone.rental.security.UserContext.clear();com.drone.rental.command.CommandKeys.clear();}
    var output=java.nio.file.Path.of(System.getenv("LEASEGUARD_PERF_EVIDENCE_DIR"));new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("worker-cohort.json").toFile(),Map.of("device",device,"initial_capacity",450,"prepared_through_real_service",true,"worker_called_directly",false,"orders",ids));
   }finally{context.close();}
  }
 }
}
