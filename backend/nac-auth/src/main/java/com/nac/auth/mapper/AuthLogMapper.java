package com.nac.auth.mapper;

import com.nac.auth.entity.AuthLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuthLogMapper {
    int insert(AuthLog log);

    @Select("SELECT COUNT(*) FROM sys_auth_log WHERE create_time >= CURDATE()")
    long countToday();

    @Select("SELECT COUNT(*) FROM sys_auth_log WHERE create_time >= CURDATE() AND result = 0")
    long countTodayFail();

    @Select("SELECT COUNT(*) FROM sys_auth_log WHERE create_time >= CURDATE() AND auth_type = #{type}")
    long countTodayByType(@Param("type") String type);

    @Select("SELECT auth_type AS name, COUNT(*) AS value FROM sys_auth_log WHERE create_time >= CURDATE() GROUP BY auth_type")
    List<Map<String, Object>> statTypeToday();

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, COUNT(*) AS value FROM sys_auth_log " +
            "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') ORDER BY day")
    List<Map<String, Object>> statTrend7d();
}
