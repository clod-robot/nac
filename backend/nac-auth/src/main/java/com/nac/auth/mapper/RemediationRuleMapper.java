package com.nac.auth.mapper;

import com.nac.auth.entity.RemediationRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 修复规则（sys_remediation_rule）。 */
@Mapper
public interface RemediationRuleMapper {

    String COLS = "id, name, trigger_type AS triggerType, trigger_value AS triggerValue, " +
            "action_type AS actionType, target_id AS targetId, action_url AS actionUrl, enabled, " +
            "create_time AS createTime, update_time AS updateTime";

    @Select("SELECT " + COLS + " FROM sys_remediation_rule ORDER BY id DESC")
    List<RemediationRule> selectAll();

    @Select("SELECT " + COLS + " FROM sys_remediation_rule WHERE enabled = 1 ORDER BY id ASC")
    List<RemediationRule> selectEnabled();

    @Insert("INSERT INTO sys_remediation_rule(name, trigger_type, trigger_value, action_type, target_id, " +
            "action_url, enabled) VALUES(#{name}, #{triggerType}, #{triggerValue}, #{actionType}, #{targetId}, " +
            "#{actionUrl}, #{enabled})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RemediationRule r);

    @Update("UPDATE sys_remediation_rule SET name = #{name}, trigger_type = #{triggerType}, " +
            "trigger_value = #{triggerValue}, action_type = #{actionType}, target_id = #{targetId}, " +
            "action_url = #{actionUrl}, enabled = #{enabled} WHERE id = #{id}")
    int update(RemediationRule r);

    @Delete("DELETE FROM sys_remediation_rule WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
