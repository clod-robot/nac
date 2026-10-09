import telnetlib, time, re

HOST="192.168.100.1"

def pump(tn, wait=2.0):
    data=b""
    end=time.time()+wait
    while time.time()<end:
        try:
            chunk=tn.read_very_eager()
        except Exception:
            chunk=b""
        if chunk:
            data+=chunk
            if b"---MORE---" in chunk:
                tn.write(b" ")
            end=time.time()+1.0
        else:
            time.sleep(0.2)
    # drain remaining pages
    for _ in range(30):
        try:
            chunk=tn.read_very_eager()
        except Exception:
            chunk=b""
        if not chunk: break
        data+=chunk
        if b"---MORE---" in data[-200:]:
            tn.write(b" ")
            time.sleep(0.5)
        else:
            break
    text=data.decode("utf-8","ignore")
    text=text.replace("---MORE---","")
    return text

def send(tn, cmd, wait=2.0):
    tn.write(cmd.encode()+b"\r\n")
    return pump(tn, wait)

tn=telnetlib.Telnet(HOST,23,timeout=10)
time.sleep(1.5)
pump(tn,1.0)
send(tn,"enable",1.5)
tn.write(b"Maipu@1993\r\n")
pump(tn,2.0)
send(tn,"",1.0)

print("==== running-config (full) ====")
cfg=send(tn,"show running-config",3.0)
print(cfg)

print("==== enter config ====")
print(send(tn,"configure terminal",2.0))
print("---- aaa accounting dot1x ? ----")
print(send(tn,"aaa accounting dot1x ?",2.0))
print("---- aaa accounting ? ----")
print(send(tn,"aaa accounting ?",2.0))
print(send(tn,"exit",1.5))
tn.write(b"exit\r\n")
