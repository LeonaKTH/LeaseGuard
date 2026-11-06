package com.drone.rental.security;
import com.drone.rental.common.exception.BusinessException;
import java.util.Objects;
public final class Access {
 private Access() {}
 public static void admin() { if (!UserContext.isAdmin()) throw new BusinessException(403,"需要管理员权限"); }
 public static void visible(Long owner) { if (UserContext.getCurrentUserId()==null || (!UserContext.isAdmin() && !Objects.equals(owner,UserContext.getCurrentUserId()))) missing(); }
 public static void owner(Long owner) { if (UserContext.getCurrentUserId()==null || !Objects.equals(owner,UserContext.getCurrentUserId())) missing(); }
 public static void missing() { throw new BusinessException(404,"资源不存在"); }
}
