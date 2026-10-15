package com.nac.auth.mapper;

import com.nac.auth.entity.OnlineSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 在线会话写入（Portal 网页认证成功后登记在线终端），与 RADIUS 计费共用 sys_online_session。
 */
@Mapper
public interface OnlineSessionMapper {

    int upsert(OnlineSession session);
}
