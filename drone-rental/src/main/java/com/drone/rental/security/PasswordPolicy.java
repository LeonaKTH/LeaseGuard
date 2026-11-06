package com.drone.rental.security;
import java.nio.charset.StandardCharsets;
import com.drone.rental.common.exception.BusinessException;
public final class PasswordPolicy {
 private PasswordPolicy() {}
 public static void validate(String p) {
  if(p==null || p.length()<12 || p.length()>64 || p.getBytes(StandardCharsets.UTF_8).length>72)
   throw new BusinessException(400,"密码须为12至64字符且UTF-8不超过72字节");
 }
}
