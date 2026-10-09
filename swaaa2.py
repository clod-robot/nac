import telnetlib, time

HOST="192.168.100.1"
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
tn.read_very_eager()
run(tn,"enable",1.5)
tn.write(b"Maipu@1993\r\n")
time.sleep(1.5)
tn.read_very_eager()

print("==== full running-config ====")
print(run(tn,"show running-config",3.0))

print("==== config: aaa accounting dot1x ? ====")
print(run(tn,"configure terminal",1.5))
print(run(tn,"aaa accounting dot1x ?",2.0))
print(run(tn,"aaa accounting dot1x start-stop ?",2.0))
print(run(tn,"exit",1.0))
tn.write(b"exit\r\n")
