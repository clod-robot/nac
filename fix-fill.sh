#!/bin/bash
pkill -f online_fill.py 2>/dev/null
pkill -f 'online-mon' 2>/dev/null
sleep 1
sed -i 's/self\.tr = t$/self.tr = tr/' /home/mao/nac/bench/online_fill.py
echo "---- fixed file ----"
sed -n '40,42p' /home/mao/nac/bench/online_fill.py
echo "---- residual ----"
pgrep -af online_fill || echo none
