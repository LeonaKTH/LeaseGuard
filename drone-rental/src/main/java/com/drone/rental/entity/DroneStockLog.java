package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 无人机库存日志实体类
 */
@Data
@TableName("drone_stock_log")
@Schema(name = "DroneStockLog", description = "无人机库存日志实体")
public class DroneStockLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "无人机ID")
    private Long droneId;

    @Schema(description = "变更类型: 1-入库, 2-出租, 3-归还, 4-维修占用, 5-维修归还")
    private Integer changeType;

    @Schema(description = "变更数量")
    private Integer changeAmount;

    @Schema(description = "变更前库存")
    private Integer beforeStock;

    @Schema(description = "变更后库存")
    private Integer afterStock;

    @Schema(description = "关联订单ID")
    private Long relatedOrderId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
