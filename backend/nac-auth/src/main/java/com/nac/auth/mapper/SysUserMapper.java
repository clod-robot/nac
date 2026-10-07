package com.nac.auth.mapper;

import com.nac.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {

    SysUser selectByUsername(@Param("username") String username);

    SysUser selectById(@Param("id") Long id);

    SysUser selectByPhoneBlind(@Param("phoneBlind") String phoneBlind);

    int insert(SysUser user);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /** 更新 RADIUS 专用可逆口令密文（AES-256-GCM） */
    int updateRadiusCipher(@Param("id") Long id, @Param("radiusPasswordCipher") String radiusPasswordCipher);

    /** 清空 RADIUS 口令（置 NULL，关闭 802.1X 准入） */
    int clearRadiusCipher(@Param("id") Long id);

    /* ---------- 账号管理（admin） ---------- */
    java.util.List<SysUser> selectPage(@Param("offset") int offset, @Param("size") int size);

    long countAll();

    int updateStatus(@Param("id") Long id, @Param("status") int status);

    int updateTerminalLimit(@Param("id") Long id, @Param("terminalLimit") int terminalLimit);

    int updateDept(@Param("id") Long id, @Param("dept") String dept);

    int deleteById(@Param("id") Long id);

    long countAdmin();
}
