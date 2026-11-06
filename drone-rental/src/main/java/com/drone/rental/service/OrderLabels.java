package com.drone.rental.service;
public final class OrderLabels {
 private OrderLabels(){}
 public static String label(Integer state){if(state==null)return "";return switch(state){case 0->"待结算";case 1->"待交付";case 2->"待接收";case 3->"租赁中";case 4->"已归还";case 5->"已取消";case 6->"已退款";default->"未知";};}
}
