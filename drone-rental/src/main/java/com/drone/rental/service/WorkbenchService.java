package com.drone.rental.service;
import com.drone.rental.security.*;
import com.drone.rental.entity.User;
import com.drone.rental.mapper.UserMapper;
import com.drone.rental.dto.TechnicianCreateDTO;
import com.drone.rental.vo.TicketView;
import com.drone.rental.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class WorkbenchService {
 private final JdbcTemplate jdbc;private final UserMapper users;private final PasswordEncoder passwords;
 public WorkbenchService(JdbcTemplate j,UserMapper u,PasswordEncoder p){jdbc=j;users=u;passwords=p;}
 private String scope(List<Object> args){Roles.staff();if(!UserContext.isAdmin()){args.add(UserContext.getCurrentUserId());return " AND t.assignee_id=?";}return "";}
 private final String select="SELECT t.*,d.model AS device_model,f.fault_type FROM maintenance_ticket t LEFT JOIN drone d ON d.id=t.drone_id AND d.deleted=0 LEFT JOIN fault_report f ON f.id=t.fault_report_id AND f.deleted=0 ";
 private final RowMapper<TicketView> row=(r,n)->new TicketView(r.getString("id"),r.getString("ticket_no"),r.getString("drone_id"),r.getString("device_model"),r.getString("fault_report_id"),r.getInt("status"),r.getInt("version"),r.getString("assignee_id"),r.getString("assignee_name"),r.getObject("assigned_at",java.time.LocalDateTime.class),r.getObject("accepted_at",java.time.LocalDateTime.class),r.getString("maintenance_description"),r.getString("fault_type"),r.getBigDecimal("actual_cost")==null?null:r.getBigDecimal("actual_cost").toPlainString(),r.getString("progress_notes"));
 public Page<TicketView> tickets(int page,int size,Integer status){ListAssembler.page(page,size);List<Object> args=new ArrayList<>();String where="WHERE t.deleted=0"+scope(args);if(status!=null){if(status<0||status>4)throw new BusinessException(400,"无效维修状态");where+=" AND t.status=?";args.add(status);}long total=jdbc.queryForObject("SELECT COUNT(*) FROM maintenance_ticket t "+where,Long.class,args.toArray());args.add(size);args.add((page-1)*size);var out=new Page<TicketView>(page,size,total);out.setRecords(jdbc.query(select+where+" ORDER BY t.created_time DESC,t.id DESC LIMIT ? OFFSET ?",row,args.toArray()));return out;}
 public TicketView ticket(long id){List<Object> args=new ArrayList<>();args.add(id);String where="WHERE t.id=? AND t.deleted=0"+scope(args);var rows=jdbc.query(select+where,row,args.toArray());if(rows.isEmpty())Access.missing();return rows.getFirst();}
 public record Technician(String id,String username,String nickname){}
 public List<Technician> technicians(){Access.admin();return jdbc.query("SELECT id,username,nickname FROM user WHERE principal_kind='USER' AND role=2 AND status=1 AND deleted=0 ORDER BY id LIMIT 100",(r,n)->new Technician(r.getString(1),r.getString(2),r.getString(3)));}
 @Transactional public Technician createTechnician(TechnicianCreateDTO dto){Access.admin();PasswordPolicy.validate(dto.password());var u=new User();u.setUsername(dto.username());u.setNickname(dto.username());u.setPassword(passwords.encode(dto.password()));u.setRole(Roles.TECHNICIAN);u.setStatus(1);u.setCreditStatus(1);users.insert(u);return new Technician(u.getId().toString(),u.getUsername(),u.getNickname());}
}
