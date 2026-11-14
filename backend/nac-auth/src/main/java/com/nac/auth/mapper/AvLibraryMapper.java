package com.nac.auth.mapper;

import com.nac.auth.entity.AvLibrary;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 病毒库（sys_av_lib）。 */
@Mapper
public interface AvLibraryMapper {

    String COLS = "id, name, vendor, version, sha256, file_url AS fileUrl, install_cmd AS installCmd, " +
            "silent_args AS silentArgs, description, enabled, publish_time AS publishTime, " +
            "create_time AS createTime, update_time AS updateTime";

    @Select("SELECT " + COLS + " FROM sys_av_lib ORDER BY id DESC")
    List<AvLibrary> selectAll();

    @Select("SELECT " + COLS + " FROM sys_av_lib WHERE enabled = 1 ORDER BY id DESC")
    List<AvLibrary> selectEnabled();

    @Select("SELECT " + COLS + " FROM sys_av_lib WHERE id = #{id}")
    AvLibrary selectById(@Param("id") Long id);

    @Insert("INSERT INTO sys_av_lib(name, vendor, version, sha256, file_url, install_cmd, silent_args, " +
            "description, enabled, publish_time) VALUES(#{name}, #{vendor}, #{version}, #{sha256}, #{fileUrl}, " +
            "#{installCmd}, #{silentArgs}, #{description}, #{enabled}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AvLibrary a);

    @Update("UPDATE sys_av_lib SET name = #{name}, vendor = #{vendor}, version = #{version}, sha256 = #{sha256}, " +
            "file_url = #{fileUrl}, install_cmd = #{installCmd}, silent_args = #{silentArgs}, " +
            "description = #{description}, enabled = #{enabled}, publish_time = #{publishTime} WHERE id = #{id}")
    int update(AvLibrary a);

    @Delete("DELETE FROM sys_av_lib WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
