package com.nac.auth.mapper;

import com.nac.auth.entity.NasDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NasMapper {
    List<NasDevice> list();

    int updateName(@Param("nasIp") String nasIp, @Param("nasName") String nasName);
}
