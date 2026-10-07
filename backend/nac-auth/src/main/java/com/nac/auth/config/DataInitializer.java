package com.nac.auth.config;

import com.nac.auth.entity.SysUser;
import com.nac.auth.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始化：确保 admin 账号存在（密码 BCrypt 编码，避免手写 hash 不一致）。
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            SysUser admin = userMapper.selectByUsername("admin");
            if (admin == null) {
                SysUser u = new SysUser();
                u.setUsername("admin");
                u.setPasswordHash(passwordEncoder.encode("admin123"));
                u.setRealName("系统管理员");
                u.setRoleCode("admin");
                u.setStatus(1);
                userMapper.insert(u);
                log.info("初始化 admin 账号完成");
            }
        } catch (Exception e) {
            log.warn("admin 初始化跳过（数据库未就绪）: {}", e.getMessage());
        }
    }
}
