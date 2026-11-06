package com.drone.rental.security;
import com.drone.rental.common.exception.BusinessException;
public final class Roles {
 public static final int USER=0,ADMIN=1,TECHNICIAN=2;
 private Roles(){}
 public static String authority(int role){return switch(role){case USER->"ROLE_USER";case ADMIN->"ROLE_ADMIN";case TECHNICIAN->"ROLE_TECHNICIAN";default->throw new BusinessException(403,"未知角色");};}
 public static void technician(){if(!Integer.valueOf(TECHNICIAN).equals(UserContext.getCurrentRole()))throw new BusinessException(403,"仅分配的技师可执行维修");}
 public static void customer(){if(!Integer.valueOf(USER).equals(UserContext.getCurrentRole())&&!UserContext.isAdmin())throw new BusinessException(403,"当前身份无租赁业务权限");}
 public static void staff(){if(!UserContext.isAdmin()&&!Integer.valueOf(TECHNICIAN).equals(UserContext.getCurrentRole()))throw new BusinessException(403,"需要维修工作台权限");}
}
