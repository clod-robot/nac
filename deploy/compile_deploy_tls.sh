#!/bin/bash
set -e
JAVAC=/home/mao/software/jdk1.8.0_202/bin/javac
JARFILE=/home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar
SRC=/tmp/EapTlsSession.java
TMP=/tmp/eap-tls-compile

echo "=== 1. 用 Python 提取 classpath ==="
rm -rf $TMP
mkdir -p $TMP/out

python3 -c "
import zipfile, os
jar_path = '$JARFILE'
tmp = '$TMP'
with zipfile.ZipFile(jar_path, 'r') as z:
    for name in z.namelist():
        if name.startswith('BOOT-INF/classes/') and not name.endswith('/'):
            z.extract(name, tmp)
        elif name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
            z.extract(name, tmp)
print('提取完成')
"

CP="$TMP/BOOT-INF/classes"
for f in $TMP/BOOT-INF/lib/*.jar; do
  CP="$CP:$f"
done

echo "=== 2. 编译 EapTlsSession.java ==="
$JAVAC -encoding UTF-8 -cp "$CP" -d $TMP/out $SRC
echo "编译成功"

echo "=== 3. 更新 fat jar ==="
cp $JARFILE ${JARFILE}.bak.$(date +%s)

python3 -c "
import zipfile, shutil
jar_path = '$JARFILE'
class_file = '$TMP/out/com/nac/auth/radius/eap/EapTlsSession.class'
entry = 'BOOT-INF/classes/com/nac/auth/radius/eap/EapTlsSession.class'
tmp_path = jar_path + '.tmp'
with zipfile.ZipFile(jar_path, 'r') as zin:
    with zipfile.ZipFile(tmp_path, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename == entry:
                continue
            zout.writestr(item, zin.read(item.filename))
        with open(class_file, 'rb') as f:
            zout.writestr(entry, f.read())
shutil.move(tmp_path, jar_path)
print('fat jar 更新成功')
"

echo "=== 4. 重启 nac-auth ==="
bash /home/mao/nac/nac.sh stop nac-auth
sleep 2
bash /home/mao/nac/nac.sh start nac-auth
sleep 3

echo "=== 5. 验证启动 ==="
tail -10 /home/mao/nac/logs/nac-auth.out
echo "=== 部署完成 ==="
