package com.drone.rental;
import com.drone.rental.security.*;
import com.drone.rental.vo.OrderVO;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class RoleContractG4Test {
 @AfterEach void clear(){UserContext.clear();}
 @Test void allSevenOrderLabelsMatchFrozenCodes(){String[] labels={"待结算","待交付","待接收","租赁中","已归还","已取消","已退款"};var vo=new OrderVO();for(int i=0;i<labels.length;i++){vo.setOrderStatus(i);assertEquals(labels[i],vo.getOrderStatusDesc());}vo.setOrderStatus(null);assertEquals("",vo.getOrderStatusDesc());vo.setOrderStatus(99);assertEquals("未知",vo.getOrderStatusDesc());}
 @Test void roleNumbersAndAuthoritiesNeverTrustAnUnknownRole(){assertEquals("ROLE_USER",Roles.authority(0));assertEquals("ROLE_ADMIN",Roles.authority(1));assertEquals("ROLE_TECHNICIAN",Roles.authority(2));assertThrows(RuntimeException.class,()->Roles.authority(3));}
 @Test void administratorIsNotTechnicianAndTechnicianNotCustomer(){UserContext.setCurrentUser(new UserContext.UserInfo(1L,"admin",1));assertThrows(RuntimeException.class,Roles::technician);UserContext.setCurrentUser(new UserContext.UserInfo(2L,"tech",2));Roles.technician();Roles.staff();assertThrows(RuntimeException.class,Roles::customer);}
 @Test void longIdsAreStringsAndRejectOverflow(){assertEquals(Long.MAX_VALUE,com.drone.rental.service.Ids.parse("9223372036854775807"));assertThrows(RuntimeException.class,()->com.drone.rental.service.Ids.parse("9223372036854775808"));assertThrows(RuntimeException.class,()->com.drone.rental.service.Ids.parse("-1"));var node=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(new com.drone.rental.service.WorkbenchService.Technician("9223372036854775807","synthetic","synthetic"));assertTrue(node.get("id").isTextual());assertEquals("9223372036854775807",node.get("id").asText());}
 @Test void domainConflictsStillMapToHttp409(){assertEquals(409,com.drone.rental.common.exception.GlobalExceptionHandler.status(1302));assertEquals(409,com.drone.rental.common.exception.GlobalExceptionHandler.status(1304));assertEquals(503,com.drone.rental.common.exception.GlobalExceptionHandler.status(503));}
}
