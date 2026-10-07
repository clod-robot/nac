package com.nac.auth.service;

import com.nac.auth.entity.ExemptTerminal;
import com.nac.auth.mapper.ExemptTerminalMapper;
import com.nac.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

/** 免认证终端白名单：命中 MAC 或 IP 即放行认证。 */
@Service
public class ExemptTerminalService {

    private final ExemptTerminalMapper mapper;

    public ExemptTerminalService(ExemptTerminalMapper mapper) {
        this.mapper = mapper;
    }

    public List<ExemptTerminal> list() {
        return mapper.selectAll();
    }

    public void create(ExemptTerminal t) {
        normalizeAndValidate(t);
        if (t.getEnabled() == null) t.setEnabled(1);
        mapper.insert(t);
    }

    public void update(ExemptTerminal t) {
        if (t.getId() == null) throw new BusinessException(400, "缺少 id");
        normalizeAndValidate(t);
        mapper.update(t);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /** 命中已启用的 MAC 或 IP 即视为免认证终端。 */
    public boolean isExempt(String mac, String ip) {
        String nMac = norm(mac);
        String nIp = ip == null ? "" : ip.trim();
        for (ExemptTerminal t : mapper.selectEnabled()) {
            if (t.getMac() != null && !t.getMac().isBlank() && norm(t.getMac()).equals(nMac) && !nMac.isEmpty())
                return true;
            if (t.getIp() != null && !t.getIp().isBlank() && t.getIp().trim().equals(nIp) && !nIp.isEmpty())
                return true;
        }
        return false;
    }

    private void normalizeAndValidate(ExemptTerminal t) {
        if (t.getMac() != null) t.setMac(t.getMac().trim());
        if (t.getIp() != null) t.setIp(t.getIp().trim());
        boolean hasMac = t.getMac() != null && !t.getMac().isBlank();
        boolean hasIp = t.getIp() != null && !t.getIp().isBlank();
        if (!hasMac && !hasIp) throw new BusinessException(400, "MAC 和 IP 至少填写一个");
        if (hasMac && norm(t.getMac()).length() != 12)
            throw new BusinessException(400, "MAC 格式不合法");
        if (hasIp && !t.getIp().matches("^[0-9a-fA-F:.]+$"))
            throw new BusinessException(400, "IP 格式不合法");
        if (hasMac) t.setMac(t.getMac().toLowerCase());
    }

    /** 归一化 MAC：去分隔符、小写，用于比较（兼容 aa:bb / aa-bb / aabb 等格式）。 */
    private String norm(String mac) {
        if (mac == null) return "";
        return mac.replaceAll("[^0-9a-fA-F]", "").toLowerCase();
    }
}
