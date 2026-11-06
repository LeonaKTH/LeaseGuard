package com.drone.rental.command;
import org.springframework.web.context.request.*;
import com.drone.rental.common.exception.BusinessException;
public final class CommandKeys {
 private static final ThreadLocal<String> LOCAL=new ThreadLocal<>();
 private CommandKeys(){}
 /** Internal callers/test harness may supply an operation ID; never sets actor/role. */
 public static void bind(String key){LOCAL.set(key);}public static void clear(){LOCAL.remove();}
 public static String key(){
  String key=LOCAL.get();
  if(key==null && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)key=a.getRequest().getHeader("Idempotency-Key");
  if(key==null)return "server-"+java.util.UUID.randomUUID();
  if(!key.matches("[A-Za-z0-9._:-]{8,128}"))throw new BusinessException(400,"Idempotency-Key须为8至128位ASCII操作ID");
  return key;
 }
}
