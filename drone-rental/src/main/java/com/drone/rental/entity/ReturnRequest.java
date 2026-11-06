package com.drone.rental.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("return_request")
public class ReturnRequest {
    private Integer version;
 @TableId(type=IdType.AUTO) private Long id;
 private Long orderId; private Long userId;
 private Integer status;
 private LocalDateTime submittedAt;private LocalDateTime reviewedAt;
 private Long reviewerId;private String reviewRemark;
}
