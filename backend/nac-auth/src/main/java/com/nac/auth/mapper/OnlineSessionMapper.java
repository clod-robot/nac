package com.nac.auth.mapper;

import com.nac.auth.entity.OnlineSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 在线会话写入（Portal 网页认证成功后登记在线终端），与 RADIUS 计费共用 sys_online_session。
 */
@Mapper
public interface OnlineSessionMapper {

    int upsert(OnlineSession session);

    /** 在线终端列表（status=1），供软件基线实时合规看板使用。 */
    @Select("SELECT id, acct_session_id, username, username_mask, mac, nas_ip, framed_ip, vlan_id, " +
            "status, start_time, update_time FROM sys_online_session WHERE status = 1 " +
            "ORDER BY update_time DESC LIMIT 500")
    List<OnlineSession> listOnline();
}
