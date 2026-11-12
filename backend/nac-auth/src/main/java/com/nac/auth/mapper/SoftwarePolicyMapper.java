package com.nac.auth.mapper;

import com.nac.auth.entity.SoftwarePolicy;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 终端软件基线策略（sys_software_policy）。 */
@Mapper
public interface SoftwarePolicyMapper {

    String COLS = "id, name, process_name, sha256, match_mode, is_required AS required, enabled, remark, create_time, update_time";

    @Select("SELECT " + COLS + " FROM sys_software_policy ORDER BY id DESC")
    List<SoftwarePolicy> selectAll();

    @Select("SELECT " + COLS + " FROM sys_software_policy WHERE enabled = 1 ORDER BY id ASC")
    List<SoftwarePolicy> selectEnabled();

    @Insert("INSERT INTO sys_software_policy(name, process_name, sha256, match_mode, is_required, enabled, remark) " +
            "VALUES(#{name}, #{processName}, #{sha256}, #{matchMode}, #{required}, #{enabled}, #{remark})")
    int insert(SoftwarePolicy p);

    @Update("UPDATE sys_software_policy SET name = #{name}, process_name = #{processName}, sha256 = #{sha256}, " +
            "match_mode = #{matchMode}, is_required = #{required}, enabled = #{enabled}, remark = #{remark} WHERE id = #{id}")
    int update(SoftwarePolicy p);

    @Delete("DELETE FROM sys_software_policy WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
