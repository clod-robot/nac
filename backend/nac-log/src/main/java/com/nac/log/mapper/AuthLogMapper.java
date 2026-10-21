package com.nac.log.mapper;

import com.nac.log.entity.AuthLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuthLogMapper {
    List<AuthLog> selectPage(@Param("offset") int offset, @Param("size") int size,
                             @Param("authType") String authType, @Param("result") Integer result,
                             @Param("username") String username, @Param("mac") String mac, @Param("ip") String ip,
                             @Param("startTime") String startTime, @Param("endTime") String endTime);

    long countAll(@Param("authType") String authType, @Param("result") Integer result,
                  @Param("username") String username, @Param("mac") String mac, @Param("ip") String ip,
                  @Param("startTime") String startTime, @Param("endTime") String endTime);

    /** 按小时统计认证成功/失败趋势（用于安全事件溯源图表） */
    List<Map<String, Object>> statTrendByHour(@Param("startTime") String startTime, @Param("endTime") String endTime);

    /** Top N 失败终端（按 MAC 分组） */
    List<Map<String, Object>> statTopFailMac(@Param("startTime") String startTime, @Param("endTime") String endTime,
                                             @Param("limit") int limit);

    /** Top N 失败用户（按 username 分组） */
    List<Map<String, Object>> statTopFailUser(@Param("startTime") String startTime, @Param("endTime") String endTime,
                                              @Param("limit") int limit);

    /** Top N 失败 NAS（按 nas_ip 分组） */
    List<Map<String, Object>> statTopFailNas(@Param("startTime") String startTime, @Param("endTime") String endTime,
                                             @Param("limit") int limit);

    /** NAS/SSID 分布（按 ssid 分组统计成功+失败总数） */
    List<Map<String, Object>> statSsidDist(@Param("startTime") String startTime, @Param("endTime") String endTime);

    /** 认证类型分布（按 auth_type 分组统计成功/失败） */
    List<Map<String, Object>> statAuthTypeDist(@Param("startTime") String startTime, @Param("endTime") String endTime);
}
