package com.nac.radius.mapper;

import com.nac.radius.entity.RadiusUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RadiusUserMapper {
    /** 按用户名查询（仅认证必要列），命中索引 uk_username */
    RadiusUser selectByUsername(@Param("username") String username);
}
