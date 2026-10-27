"""Snapshot/verify pre-existing local data across an additive application upgrade."""
from pathlib import Path
import subprocess,json,hashlib,sys
root=Path(__file__).resolve().parents[1];bundle=root/'artifacts/SRM-local-review';evidence=root/'.runtime/performance';evidence.mkdir(parents=True,exist_ok=True)
compose=['docker','compose','--env-file','runtime/compose.env']
def snapshot():
 def output(args):return subprocess.check_output(compose+args,cwd=bundle,text=True)
 rows=output(['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names snowy -e "SELECT ID,STATUS,TIER,ROW_VERSION FROM SRM_SUPPLIER ORDER BY ID"'])
 files=output(['exec','-T','backend','find','/app/files','-type','f','-exec','sha256sum','{}','+'])
 return {'suppliers':dict((r.split('\t')[0],r) for r in rows.splitlines()),'attachments':dict((r.split(maxsplit=1)[1],r.split()[0]) for r in files.splitlines()),'credentials':{n:hashlib.sha256((bundle/'runtime'/n).read_bytes()).hexdigest() for n in ['accounts.json','backend.env','compose.env','99-accounts.sql']}}
if sys.argv[1]=='snapshot':
 (evidence/'pre-upgrade.json').write_text(json.dumps(snapshot(),indent=2)+'\n');print('Original supplier rows, attachments and credential hashes captured without exposing values.')
else:
 before=json.loads((evidence/'pre-upgrade.json').read_text());after=snapshot()
 for kind,values in before.items():
  for k,v in values.items():assert after[kind].get(k)==v,f'Original {kind} changed'
 (evidence/'upgrade-preservation.json').write_text(json.dumps({'result':'passed','originalSupplierRowsPreserved':len(before['suppliers']),'originalAttachmentsPreserved':len(before['attachments']),'originalAccountsAndKeysUnchanged':True},indent=2)+'\n');print('PASS: all pre-existing supplier rows, private attachments, account files and keys preserved.')
