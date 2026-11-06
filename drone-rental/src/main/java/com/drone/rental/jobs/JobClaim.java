package com.drone.rental.jobs;
import java.time.LocalDateTime;
public record JobClaim(long id,long orderId,String token,int attempts,int maxAttempts,LocalDateTime leaseUntil) {}
