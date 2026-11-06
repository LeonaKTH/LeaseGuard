package com.drone.rental;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.sql.DataSource;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
/** Test-only actual JDBC execute instrumentation. Never records values/credentials. */
@TestConfiguration
public class SqlMeasurement {
 static class Sample {long nanos;final List<String> sql=new ArrayList<>();}
 static final ThreadLocal<Sample> CURRENT=new ThreadLocal<>();
 static Object invoke(Object target,Method m,Object[] args)throws Throwable{try{return m.invoke(target,args);}catch(InvocationTargetException ex){throw ex.getCause();}}
 static Connection connection(Connection c){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
  Object result=invoke(c,m,a);
  if(result instanceof Statement st){String sql=a!=null && a.length>0 && a[0] instanceof String?(String)a[0]:"statement";
   Class<?> type=result instanceof CallableStatement?CallableStatement.class:result instanceof PreparedStatement?PreparedStatement.class:Statement.class;
   return Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class[]{type},(proxy,method,args)->{
    var s=CURRENT.get();if(s==null || !method.getName().startsWith("execute"))return invoke(st,method,args);
    long start=System.nanoTime();try{return invoke(st,method,args);}finally{s.nanos+=System.nanoTime()-start;s.sql.add(sql.replaceAll("\\s+"," "));}
   });
  }return result;
 });}
 @Bean static BeanPostProcessor measuredDataSource(){return new BeanPostProcessor(){public Object postProcessAfterInitialization(Object bean,String name){
  if(!(bean instanceof DataSource ds))return bean;
  return new AbstractDataSource(){public Connection getConnection()throws SQLException{return connection(ds.getConnection());}public Connection getConnection(String u,String p)throws SQLException{return connection(ds.getConnection(u,p));}
   public <T>T unwrap(Class<T> c)throws SQLException{return ds.unwrap(c);}public boolean isWrapperFor(Class<?> c)throws SQLException{return ds.isWrapperFor(c);}};
 }};}
 @Bean FilterRegistrationBean<OncePerRequestFilter> measureFilter(){var registration=new FilterRegistrationBean<OncePerRequestFilter>();registration.setOrder(Integer.MIN_VALUE);
  registration.setFilter(new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,java.io.IOException{
   if(!"true".equals(request.getHeader("X-G3-Measure"))){chain.doFilter(request,response);return;}
   var s=new Sample();var wrapped=new ContentCachingResponseWrapper(response);CURRENT.set(s);
   try{chain.doFilter(request,wrapped);}finally{CURRENT.remove();wrapped.setHeader("X-G3-SQL-Count",String.valueOf(s.sql.size()));wrapped.setHeader("X-G3-DB-Nanos",String.valueOf(s.nanos));wrapped.copyBodyToResponse();LAST.set(s);}
  }});return registration;
 }
 static final java.util.concurrent.atomic.AtomicReference<Sample> LAST=new java.util.concurrent.atomic.AtomicReference<>();
}
