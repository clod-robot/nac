package com.nac.auth.mapper;

import com.nac.auth.entity.PortalConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PortalConfigMapper {
    List<PortalConfig> selectAll();

    int upsert(@Param("configKey") String configKey, @Param("configValue") String configValue, @Param("remark") String remark);
}
