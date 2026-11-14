package com.nac.radius.mapper;

import com.nac.radius.entity.PatchLibrary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 补丁库（只读）。 */
@Mapper
public interface PatchLibraryMapper {

    String COLS = "id, name, version, os_type AS osType, severity, sha256, file_url AS fileUrl, " +
            "install_cmd AS installCmd, silent_args AS silentArgs, description, enabled, publish_time AS publishTime";

    @Select("SELECT " + COLS + " FROM sys_patch_lib WHERE enabled = 1 ORDER BY id DESC")
    List<PatchLibrary> selectEnabled();

    @Select("SELECT " + COLS + " FROM sys_patch_lib WHERE id = #{id}")
    PatchLibrary selectById(@Param("id") Long id);
}
