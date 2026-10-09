package com.nac.radius.mapper;

import com.nac.common.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConfigMapper {
    SysConfig selectByKey(@Param("key") String key);

    int upsert(@Param("key") String key, @Param("value") String value);
}
