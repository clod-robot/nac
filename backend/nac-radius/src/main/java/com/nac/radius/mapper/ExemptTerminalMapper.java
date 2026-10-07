package com.nac.radius.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 读取免认证终端白名单（与 nac-auth 共享 nac 库的 auth_exempt_terminal 表）。 */
@Mapper
public interface ExemptTerminalMapper {

    @Select("SELECT mac, ip FROM auth_exempt_terminal WHERE enabled = 1")
    List<Map<String, String>> listEnabled();
}
