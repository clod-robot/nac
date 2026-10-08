package com.nac.log.mapper;

import com.nac.log.entity.OnlineSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OnlineSessionMapper {
    List<OnlineSession> selectPage(@Param("offset") int offset, @Param("size") int size,
                                   @Param("username") String username, @Param("mac") String mac, @Param("ip") String ip);

    long countAll(@Param("username") String username, @Param("mac") String mac, @Param("ip") String ip);

    int deleteById(@Param("id") Long id);
}
