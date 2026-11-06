package com.huohuo.scenicaiguide.system.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huohuo.scenicaiguide.system.entity.SysUser;
import com.huohuo.scenicaiguide.system.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Root 账号初始化器
 * <p>
 * 应用首次启动时，若不存在 root 账号则自动创建
 */
@Component
public class RootInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public RootInitializer(SysUserMapper userMapper, PasswordEncoder passwordEncoder){
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Value("${ROOT_USERNAME:root}")
    private String rootUsername;

    @Value("${ROOT_PASSWORD:root123456}")
    private String rootPassword;

    @Value("${ROOT_PHONE:13800138000}")
    private String rootPhone;

    @Override
    public void run(String... args) {
        Long count = userMapper.selectCount(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getIsRoot, 1));
        if (count != null && count > 0) {
            return;
        }

        SysUser root = new SysUser();
        root.setUsername(rootUsername);
        root.setPassword(passwordEncoder.encode(rootPassword));
        root.setPhone(rootPhone);
        root.setNickname("超级管理员");
        root.setIsRoot(1);
        root.setStatus(1);
        userMapper.insert(root);
    }
}
