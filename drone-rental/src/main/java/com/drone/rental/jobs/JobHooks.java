package com.drone.rental.jobs;
/** No production implementation or HTTP fault switch; implementations live in test sources. */
public interface JobHooks {void at(String point,JobClaim claim)throws Exception;JobHooks NONE=(p,c)->{};}
