package com.nac.auth.mapper;

import com.nac.auth.entity.IsolationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 隔离区/修复区配置（sys_isolation_config，单行 id=1）。 */
@Mapper
public interface IsolationConfigMapper {

    String COLS = "id, enabled, mode, quarantine_acl AS quarantineAcl, remediation_acl AS remediationAcl, " +
            "quarantine_vlan AS quarantineVlan, remediation_vlan AS remediationVlan, " +
            "remediation_portal_url AS remediationPortalUrl, remediation_server_ips AS remediationServerIps, " +
            "auto_release AS autoRelease, release_hold_minutes AS releaseHoldMinutes, update_time AS updateTime";

    @Select("SELECT " + COLS + " FROM sys_isolation_config WHERE id = 1")
    IsolationConfig select();

    @Update("UPDATE sys_isolation_config SET enabled = #{enabled}, mode = #{mode}, " +
            "quarantine_acl = #{quarantineAcl}, remediation_acl = #{remediationAcl}, " +
            "quarantine_vlan = #{quarantineVlan}, remediation_vlan = #{remediationVlan}, " +
            "remediation_portal_url = #{remediationPortalUrl}, remediation_server_ips = #{remediationServerIps}, " +
            "auto_release = #{autoRelease}, release_hold_minutes = #{releaseHoldMinutes} WHERE id = 1")
    int update(IsolationConfig c);
}
