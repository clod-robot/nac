package com.nac.log.mapper;

import com.nac.log.entity.AuthLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthLogMapper {
    List<AuthLog> selectPage(@Param("offset") int offset, @Param("size") int size,
                             @Param("authType") String authType, @Param("result") Integer result);

    long countAll(@Param("authType") String authType, @Param("result") Integer result);
}
