package com.drone.rental.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 无人机实体类
 */
@Data
@TableName("drone")
@Schema(name = "Drone", description = "无人机实体")
public class Drone implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "无人机ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "型号名称")
    private String model;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "类型: 航拍/测绘/农业/巡检")
    private String type;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "图片URL")
    private String image;

    @Schema(description = "每日租赁价格(元)")
    private BigDecimal pricePerDay;

    @Schema(description = "库存数量")
    private Integer stock;

    @Schema(description = "续航时间(分钟)")
    private Integer flightTime;

    @Schema(description = "最大载重(kg)")
    private BigDecimal maxPayload;

    @Schema(description = "最大速度(km/h)")
    private BigDecimal maxSpeed;

    @Schema(description = "最大航程(km)")
    private BigDecimal maxRange;

    @Schema(description = "状态: 0-缺货, 1-在售, 2-维护中")
    private Integer status;

    @Schema(description = "上架状态: 0-下架, 1-上架")
    private Integer onShelf;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @Schema(description = "逻辑删除标志")
    @TableLogic
    private Integer deleted;
}
