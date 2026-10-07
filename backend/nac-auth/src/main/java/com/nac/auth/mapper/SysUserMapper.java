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
}
