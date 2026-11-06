package com.drone.rental.jobs;
import java.time.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Configuration
public class JobClockConfiguration {
 @Bean @ConditionalOnMissingBean(Clock.class) Clock jobClock(){return Clock.system(ZoneId.of("Asia/Shanghai"));}
}
