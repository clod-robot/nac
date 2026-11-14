package com.nac.auth.mapper;

import com.nac.auth.entity.SoftwarePolicy;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 终端软件基线策略（sys_software_policy）。 */
@Mapper
public interface SoftwarePolicyMapper {

    String COLS = "id, name, process_name, sha256, match_mode, is_required AS required, enabled, " +
            "check_mode AS checkMode, popup_title AS popupTitle, popup_content AS popupContent, " +
            "popup_action_text AS popupActionText, popup_action_url AS popupActionUrl, remark, " +
            "create_time, update_time";

    @Select("SELECT " + COLS + " FROM sys_software_policy ORDER BY id DESC")
    List<SoftwarePolicy> selectAll();

    @Select("SELECT " + COLS + " FROM sys_software_policy WHERE enabled = 1 ORDER BY id ASC")
    List<SoftwarePolicy> selectEnabled();

    @Insert("INSERT INTO sys_software_policy(name, process_name, sha256, match_mode, is_required, enabled, " +
            "check_mode, popup_title, popup_content, popup_action_text, popup_action_url, remark) " +
            "VALUES(#{name}, #{processName}, #{sha256}, #{matchMode}, #{required}, #{enabled}, #{checkMode}, " +
            "#{popupTitle}, #{popupContent}, #{popupActionText}, #{popupActionUrl}, #{remark})")
    int insert(SoftwarePolicy p);

    @Update("UPDATE sys_software_policy SET name = #{name}, process_name = #{processName}, sha256 = #{sha256}, " +
            "match_mode = #{matchMode}, is_required = #{required}, enabled = #{enabled}, check_mode = #{checkMode}, " +
            "popup_title = #{popupTitle}, popup_content = #{popupContent}, popup_action_text = #{popupActionText}, " +
            "popup_action_url = #{popupActionUrl}, remark = #{remark} WHERE id = #{id}")
    int update(SoftwarePolicy p);

    @Delete("DELETE FROM sys_software_policy WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
