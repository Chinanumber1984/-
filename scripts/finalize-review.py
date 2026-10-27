"""Create a credential-free archive only after real bundle acceptance succeeds."""
from pathlib import Path
import hashlib,json,shutil,zipfile
root=Path(__file__).resolve().parents[1];bundle=root/'artifacts/SRM-local-review'
report=json.loads((root/'docs/review/verification.json').read_text())
assert report['result']=='passed' and len(report['checks'])==13
assert all(report['restart'].get(k) for k in ['actualStopAndStart','businessRowsPreserved','accountsAndKeysPreserved','privateAttachmentHashesPreserved'])
shutil.copy2(root/'docs/review/verification.json',bundle/'verification.json')
archive=root/'artifacts/SRM-local-review.zip';temporary=archive.with_suffix('.zip.tmp')
with zipfile.ZipFile(temporary,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1) as z:
 for p in sorted(bundle.rglob('*')):
  if p.is_file() and 'runtime' not in p.relative_to(bundle).parts:z.write(p,Path('SRM-local-review')/p.relative_to(bundle))
secrets=[a['password'] for a in json.loads((bundle/'runtime/accounts.json').read_text())]+[l.split('=',1)[1] for l in (bundle/'runtime/backend.env').read_text().splitlines() if '=' in l and len(l.split('=',1)[1])>=12]
with zipfile.ZipFile(temporary) as z:
 assert z.testzip() is None
 assert not any('/runtime/' in n or n.endswith(('.env','.log')) for n in z.namelist())
 for entry in z.infolist():
  if entry.file_size<1000000:assert not any(v.encode() in z.read(entry) for v in secrets),entry.filename
 assert b'SET NAMES utf8mb4;' in z.read('SRM-local-review/sql/03-srm.sql')
temporary.replace(archive)
(root/'artifacts/SRM-local-review.zip.sha256').write_text(hashlib.sha256(archive.read_bytes()).hexdigest()+'  SRM-local-review.zip\n')
print('PASS: final archive CRC, SHA256, UTF-8 initialization, acceptance evidence and credential exclusion. Size %.1f MB.'%(archive.stat().st_size/1024/1024))
