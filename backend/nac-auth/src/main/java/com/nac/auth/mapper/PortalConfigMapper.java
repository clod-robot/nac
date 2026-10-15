package com.nac.auth.mapper;

import com.nac.auth.entity.PortalConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PortalConfigMapper {
    List<PortalConfig> selectAll();

    int upsert(@Param("configKey") String configKey, @Param("configValue") String configValue, @Param("remark") String remark);

    /** 批量 upsert：一次 SQL 写入多键，避免 N 次库往返 */
    int batchUpsert(@Param("list") List<PortalConfig> list);
}
