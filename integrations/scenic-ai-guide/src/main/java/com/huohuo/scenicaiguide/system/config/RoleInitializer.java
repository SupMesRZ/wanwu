package com.huohuo.scenicaiguide.system.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huohuo.scenicaiguide.system.entity.SysRole;
import com.huohuo.scenicaiguide.system.entity.SysRolePermission;
import com.huohuo.scenicaiguide.system.mapper.SysRoleMapper;
import com.huohuo.scenicaiguide.system.mapper.SysRolePermissionMapper;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内置角色初始化器
 * <p>
 * 只在角色标识缺失时补种，已存在的内置角色不覆盖权限勾选——管理员的自定义调整优先于默认值
 * <p>
 * 初始化失败不阻断启动：root 账号本身不依赖角色，权限体系可以在系统起来后再修
 */
@Component
public class RoleInitializer implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleInitializer.class);

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    public RoleInitializer(SysRoleMapper roleMapper, SysRolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public void run(String... args) {
        try {
            seed("system-admin", "系统管理员", "除账号与角色增删改外的全部权限",
                List.copyOf(PermissionCode.allCodes()));

            seed("content-editor", "内容运营", "维护景区资产、知识库、路线与数字人配置", List.of(
                PermissionCode.SCENIC_READ.getCode(),
                PermissionCode.SCENIC_WRITE.getCode(),
                PermissionCode.MAP_PUBLISH.getCode(),
                PermissionCode.QA_READ.getCode(),
                PermissionCode.QA_WRITE.getCode(),
                PermissionCode.QA_REINDEX.getCode(),
                PermissionCode.ROUTE_READ.getCode(),
                PermissionCode.ROUTE_WRITE.getCode(),
                PermissionCode.DH_READ.getCode(),
                PermissionCode.DH_WRITE.getCode()));

            seed("viewer", "只读观察员", "只能查看，不能改动任何配置", List.of(
                PermissionCode.SCENIC_READ.getCode(),
                PermissionCode.QA_READ.getCode(),
                PermissionCode.ROUTE_READ.getCode(),
                PermissionCode.DH_READ.getCode(),
                PermissionCode.TOURIST_READ.getCode(),
                PermissionCode.ANALYTICS_READ.getCode(),
                PermissionCode.CONFIG_READ.getCode()));
        } catch (Exception e) {
            LOGGER.warn("内置角色初始化失败，可在后台手动维护：{}", e.getMessage());
        }
    }

    private void seed(String code, String name, String description, List<String> permissions) {
        Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
        if (exists != null && exists > 0) {
            return;
        }

        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setBuiltin(1);
        role.setStatus(1);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        role.setDeleted(0);
        roleMapper.insert(role);

        for (String permission : permissions) {
            SysRolePermission entry = new SysRolePermission();
            entry.setRoleId(role.getId());
            entry.setPermissionCode(permission);
            entry.setCreatedAt(LocalDateTime.now());
            rolePermissionMapper.insert(entry);
        }
        LOGGER.info("已初始化内置角色：{}（{} 项权限）", name, permissions.size());
    }
}
