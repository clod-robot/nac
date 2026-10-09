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
    java.util.List<SysUser> selectPage(@Param("offset") int offset, @Param("size") int size, @Param("keyword") String keyword, @Param("phoneBlind") String phoneBlind);

    long countAll(@Param("keyword") String keyword, @Param("phoneBlind") String phoneBlind);

    int updateStatus(@Param("id") Long id, @Param("status") int status);

    int updateTerminalLimit(@Param("id") Long id, @Param("terminalLimit") int terminalLimit);

    int updateDept(@Param("id") Long id, @Param("dept") String dept);

    int updateAuthMethod(@Param("id") Long id, @Param("authMethod") String authMethod);

    /** 更新归属部门与使用人(姓名) */
    int updateProfile(@Param("id") Long id, @Param("dept") String dept, @Param("realName") String realName);

    /** 更新联系电话（密文 + 盲索引），传 null 表示清空 */
    int updatePhone(@Param("id") Long id, @Param("phoneCipher") String phoneCipher, @Param("phoneBlind") String phoneBlind);

    int deleteById(@Param("id") Long id);

    long countAdmin();
}
