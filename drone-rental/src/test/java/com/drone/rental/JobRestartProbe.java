package com.drone.rental;
import com.drone.rental.jobs.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.time.*;
/** Test-classpath-only separate JVM. No runtime flag or HTTP route in production. */
public class JobRestartProbe {
 @TestConfiguration static class Probe {
  @Bean @Primary Clock probeClock(Environment e){return Clock.fixed(Instant.parse(e.getRequiredProperty("probe.now")),ZoneId.of("Asia/Shanghai"));}
  @Bean JobHooks crash(Environment e){return (point,claim)->{if(point.equals("after_business") && e.getProperty("probe.crash",Boolean.class,false))Runtime.getRuntime().halt(77);};}
 }
 public static void main(String[] args){
  try(var app=new SpringApplicationBuilder(DroneRentalApplication.class,Probe.class).run(args)){
   var e=app.getEnvironment();var queue=app.getBean(JobQueue.class);var claim=queue.claimNext(e.getRequiredProperty("probe.task",Long.class)).orElseThrow();
   if(!app.getBean(JobWorker.class).execute(claim).equals("DONE"))throw new IllegalStateException("Probe did not finish task");
  }
 }
}
