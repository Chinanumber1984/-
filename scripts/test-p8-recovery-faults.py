"""Restart clone-only Redis/backend and verify data and the original stay up."""
from pathlib import Path
import json,os,subprocess,time,urllib.request,urllib.error

root=Path(__file__).resolve().parents[1]
bundle=Path(os.environ['SRM_TEST_BUNDLE']).resolve()
proof=json.loads((bundle.parent/'recovery-result.json').read_text())
assert proof['project']=='srm-drill-p8-20261028' and proof['result']=='passed'
project=proof['project']
base=['docker','compose','--project-directory',str(bundle),'--env-file',str(bundle/'runtime/compose.env'),'-f',str(bundle/'compose.drill.json'),'-p',project]
actual=subprocess.check_output(['docker','inspect',project+'-mysql-1','--format','{{index .Config.Labels "com.docker.compose.project"}}'],text=True).strip()
assert actual==project
def compose(*args):return subprocess.run(base+list(args),check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
def get(url):
    try:
        with urllib.request.urlopen(url,timeout=5) as r:return r.status
    except urllib.error.HTTPError as e:return e.code
def sql(query):
    return subprocess.run(base+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names snowy'],input=query,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True).stdout.strip()
def wait_api(url,expected=200,timeout=120):
    start=time.monotonic()
    while time.monotonic()-start<timeout:
        if get(url)==expected:return round(time.monotonic()-start,2)
        time.sleep(1)
    raise RuntimeError('Readiness probe timed out')
clone=proof['entry'].rstrip('/')
live='http://localhost:18080'
assert get(live+'/')==200 and get(clone+'/')==200
before=sql("SELECT COUNT(*) FROM SRM_SUPPLIER WHERE NAME LIKE 'P8PERF-%';")
assert before=='10000'
checks=[]
try:
    compose('stop','redis');checks.append('clone Redis stopped')
    assert get(live+'/')==200 and get(live+'/api/dev/config/sysBaseList')==200
    checks.append('original instance remained available during clone Redis outage')
    compose('up','-d','--wait','--wait-timeout','120','redis')
    wait_api(clone+'/api/dev/config/sysBaseList');checks.append('clone Redis restart and API readiness')
    compose('stop','backend');checks.append('clone backend stopped')
    assert get(live+'/')==200 and get(live+'/api/dev/config/sysBaseList')==200
    checks.append('original instance remained available during clone backend outage')
    compose('up','-d','--wait','--wait-timeout','240','backend')
    elapsed=wait_api(clone+'/api/dev/config/sysBaseList',timeout=120)
    checks.append('clone backend restart and API readiness')
    assert sql("SELECT PROJECT FROM SRM_P8_DRILL_MARKER WHERE ID=1;")==project
    assert sql("SELECT COUNT(*) FROM SRM_SUPPLIER WHERE NAME LIKE 'P8PERF-%';")==before
    checks.append('clone SQL marker and 10k retained performance fixtures persisted')
    assert get(live+'/')==200
    report={'result':'passed','project':project,'cloneEntry':clone,'originalEntry':live,'checks':checks,'backendApiRestartSeconds':elapsed,'syntheticSupplierCount':10000,'fixtureCountBeforeAndAfterRestartEqual':True,'originalEntryReturned200DuringBothOutages':True}
    (root/'docs/review/P8-fault-recovery-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('PASS: clone Redis/backend recovery; original entry stayed available')
finally:
    compose('up','-d','--wait','--wait-timeout','240')
