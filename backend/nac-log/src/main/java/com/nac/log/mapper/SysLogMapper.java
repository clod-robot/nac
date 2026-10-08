package com.nac.log.mapper;

import com.nac.log.entity.SysLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysLogMapper {
    int insert(SysLog log);

    List<SysLog> selectPage(@Param("offset") int offset, @Param("size") int size,
                            @Param("operation") String operation,
                            @Param("startTime") String startTime, @Param("endTime") String endTime);

    long countAll(@Param("operation") String operation,
                  @Param("startTime") String startTime, @Param("endTime") String endTime);
}
