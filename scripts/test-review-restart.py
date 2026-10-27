from pathlib import Path
import subprocess,hashlib,json
root=Path(__file__).resolve().parents[1];bundle=root/'artifacts/SRM-local-review'
compose=['docker','compose','--env-file','runtime/compose.env']
def sql():
 return subprocess.check_output(compose+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names snowy -e "SELECT ID,STATUS FROM SRM_SUPPLIER ORDER BY ID"'],cwd=bundle,text=True)
def files():
 return subprocess.check_output(compose+['exec','-T','backend','sh','-c',r'find /app/files -type f -exec sha256sum {} \; | sort'],cwd=bundle,text=True)
before=sql();assert 'QUALIFIED' in before
file_hashes=files();assert file_hashes.strip()
hashes={n:hashlib.sha256((bundle/'runtime'/n).read_bytes()).hexdigest() for n in ['accounts.json','backend.env']}
with (root/'.runtime/review-restart.log').open('w') as log:
 subprocess.run(['bash','stop.sh'],cwd=bundle,stdout=log,stderr=subprocess.STDOUT,check=True)
 subprocess.run(['bash','start.sh'],cwd=bundle,stdout=log,stderr=subprocess.STDOUT,check=True)
assert sql()==before
assert files()==file_hashes
for n,h in hashes.items():assert hashlib.sha256((bundle/'runtime'/n).read_bytes()).hexdigest()==h
p=root/'docs/review/verification.json';report=json.loads(p.read_text());report['restart']={'result':'passed','actualStopAndStart':True,'businessRowsPreserved':True,'accountsAndKeysPreserved':True,'privateAttachmentHashesPreserved':True};p.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('PASS: actual stop/start preserves business rows, account passwords and encryption keys.')
