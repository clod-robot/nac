package com.nac.log.mapper;

import com.nac.log.entity.SysUserBrief;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统用户反查（用于安全事件溯源定位责任人）。只读，不含敏感密文字段。
 */
@Mapper
public interface SysUserBriefMapper {

    /** 按用户名精确反查（适用于 login / radius 类型日志） */
    SysUserBrief selectByUsername(@Param("username") String username);

    /** 按手机号精确反查（适用于 portal 短信认证日志，phone 存明文） */
    SysUserBrief selectByPhone(@Param("phone") String phone);
}
