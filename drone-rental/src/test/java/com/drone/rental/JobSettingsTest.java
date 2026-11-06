package com.drone.rental;
import com.drone.rental.jobs.JobSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JobSettingsTest {
 @Test void configurationAndRetryAreBounded(){var s=new JobSettings(1800,30,2,5);assertEquals(2,s.retryDelay(1).toSeconds());assertEquals(32,s.retryDelay(5).toSeconds());assertTrue(s.retryDelay(100).toSeconds()<=3600);}
 @Test void rejectUnsafeConfiguration(){assertThrows(IllegalArgumentException.class,()->new JobSettings(0,30,2,5));assertThrows(IllegalArgumentException.class,()->new JobSettings(60,301,2,5));assertThrows(IllegalArgumentException.class,()->new JobSettings(60,30,0,5));assertThrows(IllegalArgumentException.class,()->new JobSettings(60,30,2,11));}
}
