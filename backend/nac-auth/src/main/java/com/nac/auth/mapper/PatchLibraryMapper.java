package com.nac.auth.mapper;

import com.nac.auth.entity.PatchLibrary;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 补丁库（sys_patch_lib）。 */
@Mapper
public interface PatchLibraryMapper {

    String COLS = "id, name, version, os_type AS osType, severity, sha256, file_url AS fileUrl, " +
            "install_cmd AS installCmd, silent_args AS silentArgs, description, enabled, " +
            "publish_time AS publishTime, create_time AS createTime, update_time AS updateTime";

    @Select("SELECT " + COLS + " FROM sys_patch_lib ORDER BY id DESC")
    List<PatchLibrary> selectAll();

    @Select("SELECT " + COLS + " FROM sys_patch_lib WHERE enabled = 1 ORDER BY id DESC")
    List<PatchLibrary> selectEnabled();

    @Select("SELECT " + COLS + " FROM sys_patch_lib WHERE id = #{id}")
    PatchLibrary selectById(@Param("id") Long id);

    @Insert("INSERT INTO sys_patch_lib(name, version, os_type, severity, sha256, file_url, install_cmd, " +
            "silent_args, description, enabled, publish_time) VALUES(#{name}, #{version}, #{osType}, #{severity}, " +
            "#{sha256}, #{fileUrl}, #{installCmd}, #{silentArgs}, #{description}, #{enabled}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PatchLibrary p);

    @Update("UPDATE sys_patch_lib SET name = #{name}, version = #{version}, os_type = #{osType}, " +
            "severity = #{severity}, sha256 = #{sha256}, file_url = #{fileUrl}, install_cmd = #{installCmd}, " +
            "silent_args = #{silentArgs}, description = #{description}, enabled = #{enabled}, " +
            "publish_time = #{publishTime} WHERE id = #{id}")
    int update(PatchLibrary p);

    @Delete("DELETE FROM sys_patch_lib WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
