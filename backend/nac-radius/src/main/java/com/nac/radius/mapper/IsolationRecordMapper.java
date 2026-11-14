package com.nac.radius.mapper;

import com.nac.radius.entity.IsolationRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 终端隔离/修复区记录（认证链路写入 + 查询）。 */
@Mapper
public interface IsolationRecordMapper {

    String COLS = "id, mac, ip, username, nas_ip AS nasIp, zone, status, reason, " +
            "missing_json AS missingJson, channel_type AS channelType, channel_value AS channelValue, " +
            "enter_time AS enterTime, last_check_time AS lastCheckTime, release_time AS releaseTime, " +
            "release_cause AS releaseCause";

    @Select("SELECT " + COLS + " FROM sys_isolation_record WHERE mac = #{mac} AND status = 'ACTIVE' " +
            "ORDER BY id DESC LIMIT 1")
    IsolationRecord selectActiveByMac(@Param("mac") String mac);

    @Insert("INSERT INTO sys_isolation_record(mac, ip, username, nas_ip, zone, status, reason, missing_json, " +
            "channel_type, channel_value, last_check_time) VALUES(#{mac}, #{ip}, #{username}, #{nasIp}, #{zone}, " +
            "#{status}, #{reason}, #{missingJson}, #{channelType}, #{channelValue}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IsolationRecord r);

    @Update("UPDATE sys_isolation_record SET zone = #{zone}, channel_type = #{channelType}, " +
            "channel_value = #{channelValue}, last_check_time = NOW() WHERE id = #{id}")
    int updateZoneAndTouch(IsolationRecord r);

    @Update("UPDATE sys_isolation_record SET last_check_time = NOW() WHERE id = #{id}")
    int touch(@Param("id") Long id);

    @Update("UPDATE sys_isolation_record SET status = 'RELEASED', release_time = NOW(), " +
            "release_cause = #{cause}, last_check_time = NOW() WHERE id = #{id} AND status = 'ACTIVE'")
    int release(@Param("id") Long id, @Param("cause") String cause);

    @Select("SELECT " + COLS + " FROM sys_isolation_record WHERE mac = #{mac} ORDER BY id DESC LIMIT 20")
    List<IsolationRecord> selectRecentByMac(@Param("mac") String mac);
}
