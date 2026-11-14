package com.nac.radius.mapper;

import com.nac.radius.entity.IsolationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 隔离区/修复区配置（只读，单行 id=1）。 */
@Mapper
public interface IsolationConfigMapper {

    @Select("SELECT id, enabled, mode, quarantine_acl AS quarantineAcl, remediation_acl AS remediationAcl, " +
            "quarantine_vlan AS quarantineVlan, remediation_vlan AS remediationVlan, " +
            "remediation_portal_url AS remediationPortalUrl, remediation_server_ips AS remediationServerIps, " +
            "auto_release AS autoRelease, release_hold_minutes AS releaseHoldMinutes " +
            "FROM sys_isolation_config WHERE id = 1")
    IsolationConfig select();
}
