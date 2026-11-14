package com.nac.radius.mapper;

import com.nac.radius.entity.AvLibrary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 病毒库（只读）。 */
@Mapper
public interface AvLibraryMapper {

    String COLS = "id, name, vendor, version, sha256, file_url AS fileUrl, install_cmd AS installCmd, " +
            "silent_args AS silentArgs, description, enabled, publish_time AS publishTime";

    @Select("SELECT " + COLS + " FROM sys_av_lib WHERE enabled = 1 ORDER BY id DESC")
    List<AvLibrary> selectEnabled();

    @Select("SELECT " + COLS + " FROM sys_av_lib WHERE id = #{id}")
    AvLibrary selectById(@Param("id") Long id);
}
