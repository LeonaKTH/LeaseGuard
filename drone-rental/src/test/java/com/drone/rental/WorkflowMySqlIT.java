package com.drone.rental;
import com.drone.rental.entity.*;
import com.drone.rental.mapper.*;
import com.drone.rental.service.*;
import com.drone.rental.dto.*;
import com.drone.rental.security.UserContext;
import com.drone.rental.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.flywaydb.core.Flyway;
import java.time.*;
import java.math.BigDecimal;
import java.util.UUID;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class WorkflowMySqlIT {
 @Autowired JdbcTemplate jdbc;@Autowired Flyway flyway;
 @Autowired UserMapper users;@Autowired DroneMapper drones;@Autowired RentalOrderMapper orders;@Autowired FaultReportMapper faults;@Autowired MaintenanceTicketMapper tickets;
 @Autowired OrderService orderService;@Autowired MaintenanceTicketService repairs;@Autowired FaultReportService faultService;
 @Autowired PlatformTransactionManager txManager;@Autowired DroneService droneService;
 User user,admin;Drone drone;RentalOrder order;
 @BeforeEach void fixture(){
  assertEquals("leaseguard_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
  user=account(0);admin=account(1);
  drone=new Drone();drone.setModel("IT-"+UUID.randomUUID().toString().replace("-", "").substring(0,24));drone.setPricePerDay(new BigDecimal("12.50"));drone.setStock(1);drone.setStatus(1);drone.setOnShelf(1);drones.insert(drone);
  order=new RentalOrder();order.setOrderNo("IT-"+UUID.randomUUID().toString().replace("-", "").substring(0,24));order.setUserId(user.getId());order.setDroneId(drone.getId());order.setRentalStartTime(LocalDateTime.now());order.setRentalEndTime(LocalDateTime.now().plusDays(1));order.setRentalDays(2);order.setUnitPrice(new BigDecimal("12.50"));order.setTotalAmount(new BigDecimal("25.00"));order.setDepositAmount(BigDecimal.ZERO);order.setOrderStatus(3);orders.insert(order);as(user);
 }
 User account(int role){User u=new User();u.setUsername("it"+UUID.randomUUID().toString().replace("-",""));u.setPassword("unusable-test-account");u.setRole(role);u.setStatus(1);u.setCreditStatus(1);users.insert(u);return u;}
 void as(User u){UserContext.setCurrentUser(new UserContext.UserInfo(u.getId(),u.getUsername(),u.getRole()));}
 @AfterEach void cleanup(){UserContext.clear();}
 @Test void flywayMigratedDedicatedEmptySchema(){assertEquals(java.util.List.of("1","2","3","4","5","6"),java.util.Arrays.stream(flyway.info().applied()).map(i->i.getVersion().toString()).toList());assertEquals(0,flyway.info().pending().length);assertEquals("InnoDB",jdbc.queryForObject("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='return_request'",String.class));}
 @Test void persistedReturnUniqueAndApprovedOnlyOnce(){
  orderService.applyReturn(order.getId());orderService.applyReturn(order.getId());
  var r=orderService.getReturnRequest(order.getId());assertEquals(0,r.getStatus());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM return_request WHERE order_id=?",Integer.class,order.getId()));assertEquals(3,orders.selectById(order.getId()).getOrderStatus());assertEquals(1,drones.selectById(drone.getId()).getStock());
  as(admin);orderService.reviewReturn(order.getId(),true,"IT accepted");assertEquals(4,orders.selectById(order.getId()).getOrderStatus());assertEquals(2,drones.selectById(drone.getId()).getStock());assertEquals(admin.getId(),orderService.getReturnRequest(order.getId()).getReviewerId());
  assertThrows(BusinessException.class,()->orderService.confirmReturn(order.getId()));assertEquals(2,drones.selectById(drone.getId()).getStock());
 }
 @Test void rejectedReturnCanResubmitWithoutDuplicateRow(){orderService.applyReturn(order.getId());as(admin);orderService.reviewReturn(order.getId(),false,"check packaging");assertEquals(3,orders.selectById(order.getId()).getOrderStatus());as(user);orderService.applyReturn(order.getId());assertEquals(0,orderService.getReturnRequest(order.getId()).getStatus());assertNull(orderService.getReturnRequest(order.getId()).getReviewerId());assertNull(orderService.getReturnRequest(order.getId()).getReviewedAt());}
 @Test void repairSubmissionDoesNotReleaseDeviceAndPreservesOldCompleted(){
  FaultReport f=new FaultReport();f.setReportNo("IT-"+UUID.randomUUID().toString().replace("-", "").substring(0,24));f.setUserId(user.getId());f.setDroneId(drone.getId());f.setOrderId(order.getId());f.setFaultDescription("IT synthetic");f.setFaultType("IT");f.setAuditStatus(1);faults.insert(f);as(admin);repairs.createFromFaultReport(f);
  MaintenanceTicket t=repairs.getByFaultReportId(f.getId());assertEquals(2,drones.selectById(drone.getId()).getStatus());assertThrows(BusinessException.class,()->repairs.completeMaintenance(t.getId(),new MaintenanceTicketDTO()));assertEquals(0,tickets.selectById(t.getId()).getStatus());
  User tech=account(2);repairs.assign(t.getId(),tech.getId(),tickets.selectById(t.getId()).getVersion());as(tech);repairs.startMaintenance(t.getId());repairs.completeMaintenance(t.getId(),new MaintenanceTicketDTO());assertEquals(4,tickets.selectById(t.getId()).getStatus());assertEquals(2,drones.selectById(drone.getId()).getStatus());
  as(admin);var edit=new MaintenanceTicketDTO();edit.setStatus(2);assertThrows(BusinessException.class,()->repairs.updateMaintenanceTicket(t.getId(),edit));assertEquals(4,tickets.selectById(t.getId()).getStatus());
  repairs.acceptMaintenance(t.getId(),"checked");assertEquals(2,tickets.selectById(t.getId()).getStatus());assertNotNull(tickets.selectById(t.getId()).getAcceptedAt());assertEquals(1,drones.selectById(drone.getId()).getStatus());assertThrows(BusinessException.class,()->repairs.acceptMaintenance(t.getId(),"again"));
  // A historical COMPLETED=2 row remains completed and cannot be reinterpreted as pending acceptance.
  assertThrows(BusinessException.class,()->repairs.acceptMaintenance(t.getId(),"legacy"));
 }
 @Test void invisibleOrderAndFaultWritesHaveNoSideEffects(){
  as(account(0));long n=jdbc.queryForObject("SELECT COUNT(*) FROM fault_report",Long.class);
  assertEquals(404,assertThrows(BusinessException.class,()->orderService.simulatePay(order.getId(),null)).getCode());assertThrows(BusinessException.class,()->orderService.applyReturn(order.getId()));
  var dto=new FaultReportDTO();dto.setOrderId(order.getId());dto.setDroneId(drone.getId());dto.setFaultDescription("forbidden");assertThrows(BusinessException.class,()->faultService.reportFault(dto));assertEquals(n,jdbc.queryForObject("SELECT COUNT(*) FROM fault_report",Long.class));assertEquals(3,orders.selectById(order.getId()).getOrderStatus());
 }
 @Test void transactionFailureRollsBackRealMysqlInsert(){
  String marker="rollback-"+UUID.randomUUID();var template=new TransactionTemplate(txManager);template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  assertThrows(BusinessException.class,()->template.execute(status->{jdbc.update("INSERT INTO drone(model,price_per_day,stock,status,on_shelf) VALUES (?,1,1,1,1)",marker);throw new BusinessException(409,"synthetic rollback");}));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM drone WHERE model=?",Integer.class,marker));
 }
 @Test void singleRemainingStockIsNotOverwrittenByStatusUpdate(){drone.setStock(1);drones.updateById(drone);@SuppressWarnings("unused") Long id=drone.getId();
  // Minimal G1 regression for the verified stale-entity write, not a concurrency proof.
  droneService.decreaseStock(drone.getId(),1,order.getId());assertEquals(0,drones.selectById(drone.getId()).getStock());assertEquals(0,drones.selectById(drone.getId()).getStatus());droneService.increaseStock(drone.getId(),1,order.getId());assertEquals(1,drones.selectById(drone.getId()).getStock());assertEquals(1,drones.selectById(drone.getId()).getStatus());
 }
 @Test void actualRepairServiceRollsBackTicketWhenDeviceUpdateFails(){
  as(admin);
  String marker="service-rollback-"+UUID.randomUUID().toString().substring(0,8);
  FaultReport f=new FaultReport();f.setId(99999999L);f.setDroneId(99999999L);f.setUserId(user.getId());f.setFaultDescription(marker);
  var template=new TransactionTemplate(txManager);template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  assertThrows(BusinessException.class,()->template.execute(status->{repairs.createFromFaultReport(f);return null;}));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM maintenance_ticket WHERE maintenance_description=?",Integer.class,marker));
 }

}
