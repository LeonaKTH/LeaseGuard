package com.drone.rental.jobs;
import com.drone.rental.service.OrderService;
import org.springframework.stereotype.Component;
@Component
public class JobWorker {
 private final JobQueue queue;private final OrderService orders;private final JobFaults faults;
 public JobWorker(JobQueue queue,OrderService orders,JobFaults faults){this.queue=queue;this.orders=orders;this.faults=faults;}
 public void runNext(){queue.claimNext().ifPresent(this::execute);}
 public String execute(JobClaim claim){
  if(!queue.owned(claim))return "STALE";
  try{
   faults.hit("claimed",claim);faults.hit("before_business",claim);
   var result=orders.timeoutCancel(claim.orderId());
   faults.hit("after_business",claim);faults.hit("before_done",claim);
   return queue.done(claim,result)?"DONE":"STALE";
  }catch(Exception error){return queue.failed(claim,error)?"RECORDED_FAILURE":"STALE";}
 }
}
