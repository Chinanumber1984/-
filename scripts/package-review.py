from pathlib import Path
import shutil,re,hashlib,json,zipfile
root=Path(__file__).resolve().parents[1];out=root/'artifacts';bundle=out/'SRM-local-review'
out.mkdir(exist_ok=True)
if bundle.exists():raise SystemExit('Existing review bundle retained; choose a fresh output directory before rebuilding')
shutil.copytree(root/'deploy/review',bundle,ignore=shutil.ignore_patterns('runtime'))
shutil.copy2(root/'snowy-web-app/target/snowy-web-app-3.0.0.jar',bundle/'backend.jar')
shutil.copytree(root/'workspace/build',bundle/'workspace/build')
(bundle/'scripts').mkdir();shutil.copy2(root/'scripts/serve-review.mjs',bundle/'scripts/serve-review.mjs')
(bundle/'sql').mkdir();shutil.copy2(root/'snowy-web-app/src/main/resources/_sql/snowy_mysql.sql',bundle/'sql/00-snowy.sql')
for p in sorted((root/'deploy/migrations/sql').glob('V*__*.sql')):(bundle/'sql'/f'{int(p.name.split("__")[0][1:]):02d}-srm.sql').write_text('SET NAMES utf8mb4;\n'+p.read_text(),encoding='utf-8')
for dep in ['sm-crypto','jsbn']:shutil.copytree(root/'snowy-admin-web/node_modules'/dep,bundle/'vendor/node_modules'/dep)
key=re.search("'([0-9a-f]{130})'",(root/'snowy-admin-web/src/utils/smCrypto.js').read_text()).group(1);(bundle/'public-key.txt').write_text(key+'\n')
shutil.copy2(root/'LICENSE',bundle/'LICENSE');shutil.copy2(root/'docs/upstream/PROVENANCE.md',bundle/'PROVENANCE.md')
(bundle/'build-info.json').write_text(json.dumps({'backendSHA256':hashlib.sha256((bundle/'backend.jar').read_bytes()).hexdigest(),'purpose':'local review only','storage':'private local Docker volume','containsCloudCredentials':False},indent=2)+'\n')
for path in [bundle,*bundle.rglob('*')]:
 if path.is_dir():path.chmod(0o755)
 elif path.suffix=='.sh':path.chmod(0o755)
 else:path.chmod(0o644)
print('Prepared credential-free local review bundle. Start and validate it before creating the downloadable archive.')
