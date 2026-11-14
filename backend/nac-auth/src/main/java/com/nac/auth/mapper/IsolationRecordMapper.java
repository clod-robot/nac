package com.nac.auth.mapper;

import com.nac.auth.entity.IsolationRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 终端隔离/修复区记录（sys_isolation_record）。 */
@Mapper
public interface IsolationRecordMapper {

    String COLS = "id, mac, ip, username, nas_ip AS nasIp, zone, status, reason, " +
            "missing_json AS missingJson, channel_type AS channelType, channel_value AS channelValue, " +
            "enter_time AS enterTime, last_check_time AS lastCheckTime, release_time AS releaseTime, " +
            "release_cause AS releaseCause";

    @Select("SELECT " + COLS + " FROM sys_isolation_record WHERE mac = #{mac} AND status = 'ACTIVE' " +
            "ORDER BY id DESC LIMIT 1")
    IsolationRecord selectActiveByMac(@Param("mac") String mac);

    @Select("SELECT " + COLS + " FROM sys_isolation_record WHERE id = #{id}")
    IsolationRecord selectById(@Param("id") Long id);

    @Select("SELECT " + COLS + " FROM sys_isolation_record WHERE status = #{status} " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<IsolationRecord> selectByStatus(@Param("status") String status, @Param("limit") int limit);

    @Select("SELECT " + COLS + " FROM sys_isolation_record ORDER BY id DESC LIMIT #{limit}")
    List<IsolationRecord> selectRecent(@Param("limit") int limit);

    @Insert("INSERT INTO sys_isolation_record(mac, ip, username, nas_ip, zone, status, reason, missing_json, " +
            "channel_type, channel_value, last_check_time) VALUES(#{mac}, #{ip}, #{username}, #{nasIp}, #{zone}, " +
            "#{status}, #{reason}, #{missingJson}, #{channelType}, #{channelValue}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IsolationRecord r);

    @Update("UPDATE sys_isolation_record SET zone = #{zone}, channel_type = #{channelType}, " +
            "channel_value = #{channelValue}, reason = #{reason}, missing_json = #{missingJson}, " +
            "last_check_time = NOW() WHERE id = #{id}")
    int updateZone(IsolationRecord r);

    @Update("UPDATE sys_isolation_record SET last_check_time = NOW() WHERE id = #{id}")
    int touch(@Param("id") Long id);

    @Update("UPDATE sys_isolation_record SET status = 'RELEASED', release_time = NOW(), " +
            "release_cause = #{cause}, last_check_time = NOW() WHERE id = #{id} AND status = 'ACTIVE'")
    int release(@Param("id") Long id, @Param("cause") String cause);

    @Select("SELECT COUNT(1) FROM sys_isolation_record WHERE status = 'ACTIVE'")
    int countActive();

    @Select("SELECT COUNT(1) FROM sys_isolation_record WHERE status = 'ACTIVE' AND zone = #{zone}")
    int countActiveByZone(@Param("zone") String zone);

    @Select("SELECT COUNT(1) FROM sys_isolation_record WHERE status = 'RELEASED'")
    int countReleased();
}
