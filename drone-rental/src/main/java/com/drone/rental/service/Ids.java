package com.drone.rental.service;
public final class Ids {
 private Ids(){}
 public static long parse(String value){try{if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw new NumberFormatException();return Long.parseLong(value);}catch(NumberFormatException e){throw new com.drone.rental.common.exception.BusinessException(400,"ID必须是Java Long范围内的正整数字符串");}}
}
