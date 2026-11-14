package com.nac.auth.mapper;

import com.nac.auth.entity.RemediationTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 修复任务（sys_remediation_task）。 */
@Mapper
public interface RemediationTaskMapper {

    String COLS = "id, record_id AS recordId, mac, rule_id AS ruleId, lib_type AS libType, lib_id AS libId, " +
            "title, action_type AS actionType, file_url AS fileUrl, install_cmd AS installCmd, status, " +
            "result_msg AS resultMsg, create_time AS createTime, update_time AS updateTime, " +
            "finish_time AS finishTime";

    @Select("SELECT " + COLS + " FROM sys_remediation_task WHERE mac = #{mac} ORDER BY id DESC LIMIT 50")
    List<RemediationTask> selectByMac(@Param("mac") String mac);

    @Select("SELECT " + COLS + " FROM sys_remediation_task WHERE record_id = #{recordId} ORDER BY id ASC")
    List<RemediationTask> selectByRecord(@Param("recordId") Long recordId);

    @Insert("INSERT INTO sys_remediation_task(record_id, mac, rule_id, lib_type, lib_id, title, action_type, " +
            "file_url, install_cmd, status) VALUES(#{recordId}, #{mac}, #{ruleId}, #{libType}, #{libId}, " +
            "#{title}, #{actionType}, #{fileUrl}, #{installCmd}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RemediationTask t);

    @Update("UPDATE sys_remediation_task SET status = #{status}, result_msg = #{resultMsg}, " +
            "finish_time = CASE WHEN #{status} IN ('DONE','FAILED') THEN NOW() ELSE finish_time END " +
            "WHERE id = #{id} AND mac = #{mac}")
    int updateStatus(@Param("id") Long id, @Param("mac") String mac,
                     @Param("status") String status, @Param("resultMsg") String resultMsg);

    @Select("SELECT COUNT(1) FROM sys_remediation_task WHERE record_id = #{recordId} AND status NOT IN ('DONE','FAILED')")
    int countUnfinished(@Param("recordId") Long recordId);
}
