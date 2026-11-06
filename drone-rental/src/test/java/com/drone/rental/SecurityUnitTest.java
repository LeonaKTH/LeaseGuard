package com.drone.rental;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import com.drone.rental.security.*;
import com.drone.rental.common.exception.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class SecurityUnitTest {
 @AfterEach void clean(){UserContext.clear();}
 @Test void bcryptUsesSaltAndRejectsWrongCredential(){var e=new BCryptPasswordEncoder(10);String p="A-test-credential-992";String a=e.encode(p),b=e.encode(p);assertNotEquals(a,b);assertTrue(e.matches(p,a));assertFalse(e.matches("wrong",a));assertFalse(e.matches(p,"e10adc3949ba59abbe56e057f20f883e"));}
 @Test void passwordBoundsIncludeByteLimit(){assertThrows(BusinessException.class,()->PasswordPolicy.validate("123456"));assertThrows(BusinessException.class,()->PasswordPolicy.validate("汉".repeat(30)));assertDoesNotThrow(()->PasswordPolicy.validate("valid-credential-123"));}
 @Test void invisibleResourceAndAdminGuard(){UserContext.setCurrentUser(new UserContext.UserInfo(1L,"a",0));assertEquals(404,assertThrows(BusinessException.class,()->Access.owner(2L)).getCode());assertEquals(403,assertThrows(BusinessException.class,Access::admin).getCode());assertDoesNotThrow(()->Access.owner(1L));}
 @Test void adminCannotPayAsAnotherOwner(){UserContext.setCurrentUser(new UserContext.UserInfo(1L,"admin",1));assertDoesNotThrow(()->Access.visible(2L));assertThrows(BusinessException.class,()->Access.owner(2L));}
 @Test void errorStatuses(){assertEquals(401,GlobalExceptionHandler.status(1002));assertEquals(404,GlobalExceptionHandler.status(1301));assertEquals(409,GlobalExceptionHandler.status(1302));assertEquals(400,GlobalExceptionHandler.status(1901));}
}
