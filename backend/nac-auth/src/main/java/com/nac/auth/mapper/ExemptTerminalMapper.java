package com.nac.auth.mapper;

import com.nac.auth.entity.ExemptTerminal;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ExemptTerminalMapper {

    @Select("SELECT id, mac, ip, remark, enabled, create_time FROM auth_exempt_terminal ORDER BY id DESC")
    List<ExemptTerminal> selectAll();

    @Select("SELECT id, mac, ip, remark, enabled FROM auth_exempt_terminal WHERE enabled = 1")
    List<ExemptTerminal> selectEnabled();

    @Insert("INSERT INTO auth_exempt_terminal(mac, ip, remark, enabled, create_time) " +
            "VALUES(#{mac}, #{ip}, #{remark}, #{enabled}, NOW())")
    int insert(ExemptTerminal t);

    @Update("UPDATE auth_exempt_terminal SET mac = #{mac}, ip = #{ip}, remark = #{remark}, enabled = #{enabled} WHERE id = #{id}")
    int update(ExemptTerminal t);

    @Delete("DELETE FROM auth_exempt_terminal WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
