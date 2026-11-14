package com.nac.radius.mapper;

import com.nac.radius.entity.RemediationRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 修复规则（只读）。 */
@Mapper
public interface RemediationRuleMapper {

    @Select("SELECT id, name, trigger_type AS triggerType, trigger_value AS triggerValue, " +
            "action_type AS actionType, target_id AS targetId, action_url AS actionUrl, enabled " +
            "FROM sys_remediation_rule WHERE enabled = 1 ORDER BY id ASC")
    List<RemediationRule> selectEnabled();
}
