package com.drone.rental;
import com.drone.rental.command.*;
import com.drone.rental.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
class CommandUnitTest {
 @AfterEach void clear(){CommandKeys.clear();}
 @Test void objectKeyOrderHasCanonicalHashInput()throws Exception{var j=new ObjectMapper();assertEquals(CommandExecutor.canonical(j.readTree("{\"b\":2,\"a\":{\"z\":0,\"c\":1}}")),CommandExecutor.canonical(j.readTree("{\"a\":{\"c\":1,\"z\":0},\"b\":2}")));}
 @Test void parameterValuesAndArrayOrderRemainSignificant()throws Exception{var j=new ObjectMapper();assertNotEquals(CommandExecutor.canonical(j.readTree("[1,2]")),CommandExecutor.canonical(j.readTree("[2,1]")));assertNotEquals(CommandExecutor.canonical(j.readTree("{\"days\":1}")),CommandExecutor.canonical(j.readTree("{\"days\":2}")));}
 @Test void suppliedKeysAreValidatedAndStable(){CommandKeys.bind("valid-operation-001");assertEquals("valid-operation-001",CommandKeys.key());CommandKeys.bind("bad key");assertEquals(400,assertThrows(BusinessException.class,CommandKeys::key).getCode());CommandKeys.bind("x".repeat(129));assertThrows(BusinessException.class,CommandKeys::key);}
 @Test void absentLegacyHeaderGetsUniqueServerOperationId(){assertNotEquals(CommandKeys.key(),CommandKeys.key());}
}
