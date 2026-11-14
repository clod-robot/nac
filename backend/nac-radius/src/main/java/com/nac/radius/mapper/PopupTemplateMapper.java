package com.nac.radius.mapper;

import com.nac.radius.entity.PopupTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 终端合规弹窗全局模板（只读）。 */
@Mapper
public interface PopupTemplateMapper {

    @Select("SELECT id, title, content, action_text AS actionText, action_url AS actionUrl, " +
            "delay_sec AS delaySec, allow_delay AS allowDelay, enabled FROM sys_popup_template WHERE id = 1")
    PopupTemplate select();
}
