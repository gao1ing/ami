"""Functional acceptance on Windows, using temporary MySQL and Redis instances."""
import argparse
from contextlib import ExitStack
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess as sp
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--jar", type=Path, default=ROOT / "target/ami-demo-0.0.1-SNAPSHOT.jar")
parser.add_argument("--redis-server", default=shutil.which("redis-server") or "G:/env/redis/redis-server.exe")
args = parser.parse_args()
jar = args.jar.resolve(strict=True)

def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]

def stop(proc):
    if proc.poll() is None:
        proc.terminate()
        proc.wait(timeout=15)

def until(check, seconds=20):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(.2)
    raise AssertionError(f"Timed out waiting for {check}")

with tempfile.TemporaryDirectory(prefix="ami-accept-") as folder, ExitStack() as resources:
    tmp = Path(folder)
    dbport, redisport, webport = port(), port(), port()
    password = secrets.token_hex(24)
    init = tmp / "init.sql"
    init.write_text(f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{password}';\nCREATE DATABASE ami_demo;\n")
    datadir = "--datadir=" + str(tmp / "data")
    sp.run(["mysqld","--no-defaults","--initialize-insecure",datadir],check=True)
    db = sp.Popen(["mysqld","--no-defaults",datadir,"--mysqlx=OFF","--bind-address=127.0.0.1",
                   f"--port={dbport}","--init-file="+str(init)])
    cli = ["--no-defaults","--protocol=tcp","-h127.0.0.1",f"-P{dbport}","-uroot"]
    mysqlenv = dict(os.environ, MYSQL_PWD=password)
    def close_db():
        sp.run(["mysqladmin",*cli,"shutdown"],env=mysqlenv,capture_output=True)
        if db.poll() is None: db.wait(timeout=30)
    resources.callback(close_db)
    def sql(statement, check=True):
        result = sp.run(["mysql",*cli,"-NB","ami_demo"],env=mysqlenv,input=statement,text=True,capture_output=True)
        if check and result.returncode: raise RuntimeError(result.stderr)
        return result.stdout.strip()
    until(lambda: sql("SELECT 1",False)=="1")
    for file in ["01-schema.sql","02-sample.sql"]:
        sql((ROOT / "src/main/resources/db" / file).read_text(encoding="utf-8"))
    redis = sp.Popen([args.redis_server,"--bind","127.0.0.1","--port",str(redisport),
                      "--appendonly","no"],cwd=tmp,stdout=sp.DEVNULL)
    resources.callback(stop,redis)
    log_path = tmp / "app.log"
    def start_app():
        log = resources.enter_context(log_path.open("ab"))
        env = dict(os.environ, AMI_DB_USERNAME="root", AMI_DB_PASSWORD=password, AMI_PORT=str(webport),
                   AMI_REDIS_HOST="127.0.0.1", AMI_REDIS_PORT=str(redisport),
                   AMI_DB_URL=f"jdbc:mysql://127.0.0.1:{dbport}/ami_demo?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true")
        java = Path(os.environ["JAVA_HOME"]) / "bin/java.exe"
        proc = sp.Popen([str(java),"-jar",str(jar),"--ami.heartbeat.sync-delay=1s",
                         "--ami.heartbeat.scan-delay=500ms","--ami.heartbeat.timeout-seconds=6",
                         "--logging.level.com.example.ami.cache.DailyCurveCache=DEBUG"],
                        env=env,stdout=log,stderr=log)
        resources.callback(stop,proc)
        def ready():
            if proc.poll() is not None: raise RuntimeError(log_path.read_text(errors="replace")[-4000:])
            try: return http("GET","/actuator/health/readiness")[0]==200
            except URLError: return False
        until(ready,35)
        return proc
    def http(method,path,body=None):
        req = Request(f"http://127.0.0.1:{webport}"+path,method=method,
                      data=json.dumps(body).encode() if body is not None else None,
                      headers={"Content-Type":"application/json"})
        try:
            with urlopen(req,timeout=8) as res:
                raw = res.read()
                return res.status, json.loads(raw) if raw else None
        except HTTPError as e: return e.code, None
    app = start_app()
    route = "/devices/M001/daily-curves?date=2026-11-25"
    def voltage():
        code,body=http("GET",route)
        assert code==200 and len(body["points"])==10
        return next(p["value"] for p in body["points"] if p["curveType"]=="VOLTAGE_A")
    assert voltage()==220.1 and voltage()==220.1
    stop(app)
    app=start_app()
    assert voltage()==220.1
    messages=log_path.read_text(encoding="utf-8",errors="replace")
    for source in ("回源 MySQL","命中 Caffeine","命中 Redis"): assert source in messages,source
    correction={"curveType":"VOLTAGE_A","sampleTime":"2026-11-25T05:00:00Z","value":220.4}
    for _ in range(2): assert http("PATCH","/devices/M001/curve-readings",correction)[0]==204
    assert voltage()==220.4
    assert http("PATCH","/devices/M001/curve-readings",{**correction,"sampleTime":"2026-11-25T05:15:00Z"})[0]==404
    search={"from":"2026-11-25T05:00:00Z","to":"2026-11-26T05:00:00Z","minKwh":1,"page":1,"size":1}
    for page,val,more in [(1,1.25,True),(2,2.5,False)]:
        code,b=http("POST","/curve-readings/search",{**search,"page":page})
        assert code==200 and b["items"][0]["value"]==val and b["hasNext"]==more
    print("PASS daily curve, L1/L2 and MySQL, correction, conditional search",flush=True)
    def state():
        code,b=http("GET","/devices/status")
        assert code==200
        return next(i for i in b["items"] if i["deviceNo"]=="M003")
    assert state()["state"]=="NEVER"
    assert http("GET","/devices/status?size=1")[1]["nextAfterId"] is not None
    event={"eventId":"demo-1","reportedAt":"2025-01-01T00:00:00Z"}
    def send(event_id):
        assert http("POST","/devices/M003/heartbeats",{**event,"eventId":event_id})[0]==202
    send("demo-1")
    send("demo-1")
    until(lambda: state()["state"]=="ONLINE")
    last=state()["lastReceivedAt"]
    assert sql("SELECT COUNT(*) FROM heartbeat_event WHERE source_event_id='demo-1'")=="1"
    sql("INSERT INTO heartbeat_event(device_id,source_event_id,reported_at,received_at) SELECT id,'old',UTC_TIMESTAMP()-INTERVAL 1 DAY,UTC_TIMESTAMP()-INTERVAL 1 DAY FROM device WHERE device_no='M003'")
    until(lambda: sql("SELECT processed_at IS NOT NULL FROM heartbeat_event WHERE source_event_id='old'")=="1")
    assert state()["lastReceivedAt"]==last
    until(lambda: state()["state"]=="OFFLINE")
    # Fail the last statement in the transaction. Snapshot and completion must both roll back.
    sql("CREATE TRIGGER fail_batch BEFORE UPDATE ON heartbeat_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='acceptance rollback'")
    send("recovery")
    until(lambda: "acceptance rollback" in log_path.read_text(errors="replace"))
    assert state()["state"]=="OFFLINE"
    assert sql("SELECT processed_at IS NULL FROM heartbeat_event WHERE source_event_id='recovery'")=="1"
    # Simulate an expired online snapshot with a fresh pending event. Scan must spare it.
    sql("UPDATE device_status SET state='ONLINE', last_received_at=UTC_TIMESTAMP()-INTERVAL 1 HOUR WHERE device_id=(SELECT id FROM device WHERE device_no='M003')")
    time.sleep(1.3)
    assert state()["state"]=="ONLINE"
    sql("DROP TRIGGER fail_batch")
    until(lambda: sql("SELECT processed_at IS NOT NULL FROM heartbeat_event WHERE source_event_id='recovery'")=="1")
    assert state()["state"]=="ONLINE" and state()["lastReceivedAt"]>last
    until(lambda: state()["state"]=="OFFLINE")
    send("online-again")
    until(lambda: state()["state"]=="ONLINE")
    counts=http("GET","/devices/status/summary")[1]
    assert sum(counts.values())==3 and counts["ONLINE"]>=1
    assert all(i["state"]=="ONLINE" for i in http("GET","/devices/status?state=ONLINE")[1]["items"])
    print("PASS scheduling, duplicate and late events, offline/recovery, rollback/retry, backlog and summary")
