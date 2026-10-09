import telnetlib, time, sys

HOST="192.168.100.1"; USER=""; PWD=""
def run(tn, cmd, wait=2.0):
    tn.write(cmd.encode()+b"\r\n")
    time.sleep(wait)
    data=b""
    while True:
        try:
            chunk=tn.read_very_eager()
        except Exception:
            break
        if not chunk: break
        data+=chunk
        time.sleep(0.3)
    return data.decode("utf-8","ignore")

tn=telnetlib.Telnet(HOST,23,timeout=10)
time.sleep(1.5)
print(tn.read_very_eager().decode("utf-8","ignore"))
# no login on this switch; go enable
print(run(tn,"enable",1.5))
tn.write(b"Maipu@1993\r\n")
time.sleep(1.5)
print(tn.read_very_eager().decode("utf-8","ignore"))
tn.write(b"terminal length 0\r\n")
time.sleep(1.0)
print(tn.read_very_eager().decode("utf-8","ignore"))
for cmd in [
  "show running-config | include aaa",
  "show running-config | include tacacs",
  "show running-config | include radius",
  "show aaa method-list",
  "show tacacs",
]:
    print("==== %s ====" % cmd)
    print(run(tn,cmd,2.5))
tn.write(b"exit\r\n")
