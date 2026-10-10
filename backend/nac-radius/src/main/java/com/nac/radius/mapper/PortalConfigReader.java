package com.nac.radius.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 读取 sys_portal_config（与 auth 模块共用同一张配置表）。
 * 仅 radius 侧只读访问，用于 Portal v2.0 协议服务端热加载开关/端口/共享密钥。
 */
@Mapper
public interface PortalConfigReader {

    @Select("SELECT config_value FROM sys_portal_config WHERE config_key = #{key}")
    String selectValue(@Param("key") String key);
}
