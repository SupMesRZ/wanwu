package com.huohuo.scenicaiguide.scenic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标签创建/更新请求
 */
@Data
public class ScenicTagRequest {

    @NotBlank(message = "标签标识不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9-]{0,49}$", message = "标签标识需为英文小写、数字或中划线")
    private String code;

    @NotBlank(message = "标签名称不能为空")
    @Size(max = 50, message = "标签名称不能超过50字")
    private String name;

    @Size(max = 20, message = "颜色值过长")
    private String color;

    @Size(max = 255, message = "说明不能超过255字")
    private String description;

    private Integer status;
}
