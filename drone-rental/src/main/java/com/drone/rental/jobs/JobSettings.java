package com.drone.rental.jobs;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
@Component
public class JobSettings {
 public final Duration timeout,lease,backoff;public final int maxAttempts;
 public JobSettings(@Value("${leaseguard.jobs.timeout-seconds:1800}")long timeout,@Value("${leaseguard.jobs.lease-seconds:30}")long lease,@Value("${leaseguard.jobs.backoff-seconds:2}")long backoff,@Value("${leaseguard.jobs.max-attempts:5}")int max){
  if(timeout<1||timeout>86400||lease<1||lease>300||backoff<1||backoff>60||max<1||max>10)throw new IllegalArgumentException("Invalid bounded job configuration");
  this.timeout=Duration.ofSeconds(timeout);this.lease=Duration.ofSeconds(lease);this.backoff=Duration.ofSeconds(backoff);this.maxAttempts=max;
 }
 public Duration retryDelay(int attempts){return Duration.ofSeconds(Math.min(3600,backoff.toSeconds()*(1L<<Math.min(attempts-1,10))));}
}
