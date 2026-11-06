package com.drone.rental.jobs;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
@Component
public class JobFaults {
 private final ObjectProvider<JobHooks> hooks;
 public JobFaults(ObjectProvider<JobHooks> hooks){this.hooks=hooks;}
 public void hit(String point,JobClaim claim){try{hooks.getIfAvailable(()->JobHooks.NONE).at(point,claim);}catch(RuntimeException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("Job interrupted",ex);}}
}
