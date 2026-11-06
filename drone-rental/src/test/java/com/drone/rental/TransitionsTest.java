package com.drone.rental;
import com.drone.rental.service.Transitions;
import com.drone.rental.common.exception.BusinessException;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class TransitionsTest {
 @Test void preserveHistoricalCompletedEncoding(){assertEquals(2,Transitions.REPAIR_COMPLETED);assertEquals(4,Transitions.REPAIR_WAIT_ACCEPTANCE);}
 @Test void rejectSkippedCompletionAndDoubleAcceptance(){assertThrows(BusinessException.class,()->Transitions.require(0,1));assertThrows(BusinessException.class,()->Transitions.require(2,4));assertDoesNotThrow(()->Transitions.require(4,4));}
}
