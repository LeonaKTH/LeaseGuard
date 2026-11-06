package com.drone.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 无人机创建/更新DTO
 */
@Data
@Schema(description = "无人机参数")
public class DroneDTO {

    @NotBlank(message = "型号名称不能为空")
    @Schema(description = "型号名称", required = true)
    private String model;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "类型: 航拍/测绘/农业/巡检")
    private String type;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "图片URL")
    private String image;

    @NotNull(message = "每日租赁价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    @Schema(description = "每日租赁价格(元)", required = true)
    private BigDecimal pricePerDay;

    @NotNull(message = "库存数量不能为空")
    @Min(value = 0, message = "库存数量不能为负数")
    @Schema(description = "库存数量", required = true)
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
}
