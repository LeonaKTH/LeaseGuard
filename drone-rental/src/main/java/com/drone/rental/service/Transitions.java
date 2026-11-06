package com.drone.rental.service;
import com.drone.rental.common.exception.BusinessException;
public final class Transitions {
 private Transitions() {}
 public static void require(Integer actual,int expected){if(actual==null || actual!=expected)throw new BusinessException(409,"当前状态不允许该操作");}
 public static final int REPAIR_PENDING=0,REPAIR_IN_PROGRESS=1,REPAIR_COMPLETED=2,REPAIR_WAIT_ACCEPTANCE=4;
}
