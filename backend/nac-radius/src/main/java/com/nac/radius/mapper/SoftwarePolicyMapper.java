package com.nac.radius.mapper;

import com.nac.radius.entity.SoftwarePolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 读取终端软件基线策略（与 nac-auth 共享 nac 库的 sys_software_policy 表）。 */
@Mapper
public interface SoftwarePolicyMapper {

    @Select("SELECT id, name, process_name, sha256, match_mode, is_required AS required, enabled " +
            "FROM sys_software_policy WHERE enabled = 1 ORDER BY id ASC")
    List<SoftwarePolicy> selectEnabled();
}
