package com.nac.auth.mapper;

import com.nac.auth.entity.AuthLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthLogMapper {
    int insert(AuthLog log);
}
