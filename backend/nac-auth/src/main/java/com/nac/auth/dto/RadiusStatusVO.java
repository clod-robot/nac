package com.nac.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** RADIUS 开通状态视图。仅暴露布尔状态，不返回任何密文/明文。 */
@Data
@AllArgsConstructor
public class RadiusStatusVO {
    private String username;
    private boolean enabled;   // 是否已设置 RADIUS 口令
    private Integer status;    // 账号状态 1启用 0禁用
}
