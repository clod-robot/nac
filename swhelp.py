import telnetlib, time

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
            end=time.time()+1.0
        else:
            time.sleep(0.2)
    return data.decode("utf-8","ignore")

def send(tn, cmd, wait=2.0):
    tn.write(cmd.encode()+b"\r\n")
    return pump(tn, wait)

tn=telnetlib.Telnet(HOST,23,timeout=10)
time.sleep(1.5); pump(tn,1.0)
send(tn,"enable",1.5)
tn.write(b"Maipu@1993\r\n"); pump(tn,2.0)
send(tn,"",1.0)
send(tn,"configure terminal",1.5)
print(send(tn,"domain system",1.5))
print("---- aaa accounting dot1x ? ----")
print(send(tn,"aaa accounting dot1x ?",2.0))
print("---- aaa accounting dot1x start-stop ? ----")
print(send(tn,"aaa accounting dot1x start-stop ?",2.0))
print("---- aaa accounting dot1x start-stop radius-group ? ----")
print(send(tn,"aaa accounting dot1x start-stop radius-group ?",2.0))
print(send(tn,"exit",1.0))
print(send(tn,"exit",1.0))
