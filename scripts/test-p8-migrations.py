"""Read-only compare applied migration ledger with source and packaged SQL."""
from pathlib import Path
import hashlib,json,subprocess

root=Path(__file__).resolve().parents[1]
bundle=Path('.runtime/pilot/recovery-20261028/bundle').resolve()
proof=json.loads((bundle.parent/'recovery-result.json').read_text())
assert proof['project']=='srm-drill-p8-20261028' and proof['result']=='passed'
cmd=['docker','compose','--project-directory',str(bundle),'--env-file',str(bundle/'runtime/compose.env'),'-f',str(bundle/'compose.drill.json'),'-p',proof['project'],'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names snowy']
rows=subprocess.run(cmd,input='SELECT VERSION,CHECKSUM,STATUS FROM SRM_REVIEW_MIGRATION WHERE VERSION BETWEEN 6 AND 9 ORDER BY VERSION;',text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True).stdout.splitlines()
actual={int(v):(digest,status) for v,digest,status in (r.split('\t') for r in rows)}
expected={}
packaged={}
for version in range(6,10):
    srcs=list((root/'deploy/migrations/sql').glob(f'V{version}__*.sql'))
    assert len(srcs)==1
    src=hashlib.sha256(srcs[0].read_bytes()).hexdigest()
    pack=root/'artifacts/SRM-local-review/sql'/f'{version:02d}-srm.sql'
    data=pack.read_bytes().splitlines(keepends=True)
    pkg=hashlib.sha256(b''.join(data[1:])).hexdigest()
    expected[version]=src;packaged[version]=pkg
    assert src==pkg,f'Packaged migration checksum differs for V{version}'
    assert actual.get(version)==(src,'APPLIED'),f'Applied ledger differs for V{version}'
report={'result':'passed','project':proof['project'],'versions':{str(v):{'sourceSha256':expected[v],'packageSha256':packaged[v],'databaseStatus':actual[v][1],'databaseSha256':actual[v][0]}for v in range(6,10)},'databaseModified':False,'check':'read-only; no migration was replayed'}
dest=root/'docs/review/P8-migration-verification.json';dest.write_text(json.dumps(report,indent=2)+'\n')
print('PASS: V6–V9 source, package, and applied ledger SHA-256 match')
