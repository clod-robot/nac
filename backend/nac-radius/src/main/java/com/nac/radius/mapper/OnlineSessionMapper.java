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

    /** 同 MAC 新会话上线时，把该 MAC 其它在线旧会话置离线 */
    int markOthersOffline(@Param("mac") String mac, @Param("acctSessionId") String acctSessionId);

    /** 超过指定分钟未更新的在线会话置离线（兜底清扫） */
    int markStaleOffline(@Param("minutes") int minutes);

    /** 取某 MAC 最近一条会话（按 id 倒序），用于判断是否短时间内重认证 */
    OnlineSession selectLatestByMac(@Param("mac") String mac);

    OnlineSession selectBySessionId(@Param("acctSessionId") String acctSessionId);
}
