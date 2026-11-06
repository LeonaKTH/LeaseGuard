package com.drone.rental.jobs;
import java.time.*;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
@Configuration
public class JobConfiguration {
 @Configuration @EnableScheduling @ConditionalOnProperty(name="leaseguard.jobs.enabled",havingValue="true")
 static class ScheduledJobs {
  @Bean(destroyMethod="shutdownNow") ThreadPoolExecutor timeoutPool(){return new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{var t=new Thread(r,"leaseguard-timeout");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());}
  @Bean Poller timeoutPoller(ThreadPoolExecutor timeoutPool,JobWorker worker){return new Poller(timeoutPool,worker);}
 }
 static class Poller {
  final ThreadPoolExecutor pool;final JobWorker worker;
  Poller(ThreadPoolExecutor pool,JobWorker worker){this.pool=pool;this.worker=worker;}
  @Scheduled(fixedDelayString="${leaseguard.jobs.poll-millis:1000}") public void poll(){
   // Claim only after an execution slot is accepted; saturation leaves tasks in MySQL.
   for(int i=0;i<2;i++)try{pool.execute(worker::runNext);}catch(RejectedExecutionException full){break;}
  }
 }
}
