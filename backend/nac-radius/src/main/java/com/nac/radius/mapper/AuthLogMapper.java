package com.nac.radius.mapper;

import com.nac.radius.entity.AuthLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthLogMapper {
    int insert(AuthLog log);
}
