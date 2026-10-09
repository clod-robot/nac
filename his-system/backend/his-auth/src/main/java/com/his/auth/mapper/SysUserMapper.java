package com.his.auth.mapper;

import com.his.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper {

    @Select("SELECT id, username, password, real_name AS realName, role, phone, status, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM sys_user WHERE username = #{username} AND status = 1 LIMIT 1")
    SysUser selectByUsername(@Param("username") String username);

    @Select("SELECT id, username, real_name AS realName, role, phone FROM sys_user WHERE id = #{id}")
    SysUser selectById(@Param("id") Long id);
}
