package com.drone.rental.vo;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description="授权维修工作台白名单：ID和金额使用字符串，无用户隐私、凭据或内部异常")
public record TicketView(String id,String ticketNo,String droneId,String droneModel,String faultReportId,int status,int version,String assigneeId,String assigneeName,LocalDateTime assignedAt,LocalDateTime acceptedAt,String description,String faultType,String actualCost,String progressNotes) {}
