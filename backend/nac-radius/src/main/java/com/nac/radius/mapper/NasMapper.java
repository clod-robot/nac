package com.nac.radius.mapper;

import com.nac.radius.entity.NasDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NasMapper {
    /** 认证时按 nas_ip 登记/更新：成功/失败计数 +1，刷新 last_seen（幂等 upsert） */
    int touch(@Param("nasIp") String nasIp, @Param("nasIdentifier") String nasIdentifier,
              @Param("lastUser") String lastUser, @Param("success") int success, @Param("fail") int fail);

    List<NasDevice> list();

    int updateName(@Param("nasIp") String nasIp, @Param("nasName") String nasName);
}
