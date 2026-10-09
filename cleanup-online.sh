#!/bin/bash
# 清理所有 online 测试残留（精确匹配 python 与监控循环，不匹配本脚本命令行）
pkill -f 'python3 online_fill.py' 2>/dev/null
pkill -f 'online-mon.csv' 2>/dev/null
for p in $(pgrep -f 'online-test.sh'); do
  [ "$p" != "$$" ] && kill "$p" 2>/dev/null
done
sleep 2
echo "remaining:"
pgrep -af 'online_fill.py' || echo "no python"
pgrep -af 'online-mon' || echo "no monitor"
ps aux | grep -c '[o]nline-test.sh'
