package com.huohuo.scenicaiguide.system.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理员账号新增/编辑请求
 *
 * <p>新增时 username 与 password 必填，编辑时 password 留空表示不修改
 */
@Data
public class AdminUserSaveRequest {

    @Size(min = 3, max = 32, message = "用户名长度需在 3-32 之间")
    private String username;

    @Size(max = 32, message = "昵称最长 32 个字符")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
    private String password;

    /** 启用状态：1 启用，0 禁用 */
    private Integer status;

    /** 分配的角色ID，null 表示不改动角色分配，空数组表示清空 */
    private List<Long> roleIds;
}
