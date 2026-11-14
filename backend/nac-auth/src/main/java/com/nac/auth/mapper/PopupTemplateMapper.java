package com.nac.auth.mapper;

import com.nac.auth.entity.PopupTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 终端合规弹窗全局模板（sys_popup_template，单行 id=1）。 */
@Mapper
public interface PopupTemplateMapper {

    String COLS = "id, title, content, action_text AS actionText, action_url AS actionUrl, " +
            "delay_sec AS delaySec, allow_delay AS allowDelay, enabled, update_time AS updateTime";

    @Select("SELECT " + COLS + " FROM sys_popup_template WHERE id = 1")
    PopupTemplate select();

    @Update("UPDATE sys_popup_template SET title = #{title}, content = #{content}, " +
            "action_text = #{actionText}, action_url = #{actionUrl}, delay_sec = #{delaySec}, " +
            "allow_delay = #{allowDelay}, enabled = #{enabled} WHERE id = 1")
    int update(PopupTemplate t);
}
