#!/bin/bash
PW='${SUDO_PW}'
sd() { echo "$PW" | sudo -S -k "$@" 2>/dev/null; }

echo "===== 备份现有前端 ====="
sd rm -rf /var/www/nac.bak-urlparam
sd cp -a /var/www/nac /var/www/nac.bak-urlparam

echo "===== 清空并解压新前端 ====="
sd bash -c 'rm -rf /var/www/nac/* && tar xzf /home/mao/dist.tgz -C /var/www/nac'
ls -la /var/www/nac | head -8

echo "===== 冒烟 ====="
curl -sk -o /dev/null -w 'index=%{http_code}\n' https://127.0.0.1/nac/
echo "FRONT_DONE"
