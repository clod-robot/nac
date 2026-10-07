package com.nac.radius.mapper;

import com.nac.radius.entity.OnlineSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OnlineSessionMapper {

    /** 会话存在则更新，否则插入（基于唯一键 uk_session 的 upsert） */
    int upsert(OnlineSession session);

    /** 会话结束：置离线 */
    int markOffline(@Param("acctSessionId") String acctSessionId);

    OnlineSession selectBySessionId(@Param("acctSessionId") String acctSessionId);
}
