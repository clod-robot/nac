#!/bin/bash
# 收尾：持久化 UDP 缓冲参数 + 清理压测会话 + 最终状态
RPWD="${SUDO_PW}"
echo "===== persist sysctl ====="
echo '${SUDO_PW}' | sudo -S bash -c 'cat > /etc/sysctl.d/99-nac-radius.conf <<EOF
net.core.rmem_max=4194304
net.core.wmem_max=4194304
EOF
sysctl -p /etc/sysctl.d/99-nac-radius.conf'
echo "===== cleanup bench sessions ====="
redis-cli -a "$RPWD" --no-auth-warning --scan --pattern 'nac:online:*' \
  | xargs -r -n 200 redis-cli -a "$RPWD" --no-auth-warning UNLINK >/dev/null
echo "SCARD after cleanup: $(redis-cli -a "$RPWD" --no-auth-warning SCARD nac:online:ids)"
echo "===== services ====="
ss -tln | grep -cE ':900[0-6] '
ss -uln | grep -c ':1812'
echo "===== machine ====="
uptime
free -m | sed -n '1,2p'
echo "===== redis host effective ====="
grep -o "spring.redis.host=[^ ]*" ~/nac/nac.sh
