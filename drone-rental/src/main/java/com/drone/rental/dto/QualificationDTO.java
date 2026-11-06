package com.drone.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 飞行资质提交DTO
 */
@Data
@Schema(description = "飞行资质提交参数")
public class QualificationDTO {

    @NotBlank(message = "证件号码不能为空")
    @Schema(description = "证件号码", required = true)
    private String certificateNo;

    @Schema(description = "证件类型")
    private String certificateType;

    @Schema(description = "证件图片URL")
    private String certificateImage;

    @NotNull(message = "有效期开始日期不能为空")
    @Schema(description = "有效期开始日期", required = true)
    private LocalDate validStartDate;

    @NotNull(message = "有效期结束日期不能为空")
    @Schema(description = "有效期结束日期", required = true)
    private LocalDate validEndDate;
}
