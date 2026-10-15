#!/bin/bash
# 去掉 nac.sh 中的 spring.redis.host 覆盖（源码已改），然后重启全部服务
sed -i 's/ --spring.redis.host=127.0.0.1//' ~/nac/nac.sh
echo "---- nac-auth line now ----"
grep nac-auth ~/nac/nac.sh
echo "==== stop-all ===="
bash ~/nac/nac.sh stop-all 2>&1 | tail -8
sleep 3
echo "==== start-all ===="
bash ~/nac/nac.sh start-all 2>&1 | tail -9
echo "WAITING"
for i in $(seq 1 40); do
  sleep 2
  n=$(ss -tln | grep -cE ':900[0-6] ')
  u=$(ss -uln | grep -c ':1812')
  if [ "$n" = "7" ] && [ "$u" = "1" ]; then
    echo "ALL_PORTS_READY after $((i*2))s"
    break
  fi
done
