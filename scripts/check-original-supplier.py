from pathlib import Path
import gzip,re,json,subprocess,hashlib
root=Path(__file__).resolve().parents[1];baseline=json.loads((root/'.runtime/quality/preservation.json').read_text());bundle=root/'artifacts/SRM-local-review';sid=json.loads((bundle/'runtime/account-profile.json').read_text())['supplierId']
backup=Path((root/'.runtime/quality/preservation-backup.txt').read_text().strip())
text=gzip.open(backup,'rt').read();create=re.search(r'CREATE TABLE `SRM_SUPPLIER` \(.*?;\n',text,re.S).group();inserts=re.findall(r'INSERT INTO `SRM_SUPPLIER` VALUES .*?;\n',text)
# Temporary table only, in this single connection; it disappears when the connection closes.
create=create.replace('CREATE TABLE `SRM_SUPPLIER`','CREATE TEMPORARY TABLE `SRM_P7_BEFORE_SUPPLIER`');inserts=[s.replace('INSERT INTO `SRM_SUPPLIER`','INSERT INTO `SRM_P7_BEFORE_SUPPLIER`') for s in inserts]
def sql(s):return subprocess.check_output(['docker','compose','--env-file','runtime/compose.env','exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot --batch --skip-column-names snowy'],cwd=bundle,input=s,text=True).strip()
columns=sql("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='snowy' AND TABLE_NAME='SRM_SUPPLIER' ORDER BY ORDINAL_POSITION").splitlines();columns=[f"IF(ID='{sid}',NULL,`{c}`)" if c in {'ROW_VERSION','UPDATE_TIME','UPDATE_USER'} else '`'+c+'`' for c in columns]
ids=baseline['rows']['SRM_SUPPLIER']['ids'];where=' WHERE ID IN ('+','.join("'"+s+"'" for s in ids)+') ORDER BY ID';projection=','.join(columns)
old=sql(create+''.join(inserts)+'SELECT '+projection+' FROM SRM_P7_BEFORE_SUPPLIER'+where+';');current=sql('SELECT '+projection+' FROM SRM_SUPPLIER'+where+';')
assert current==old,'Business columns changed beyond the one audited tier correction'
proof={'result':'passed','scope':'All original supplier business columns; only target supplier ROW_VERSION/UPDATE_TIME/UPDATE_USER excluded for the audited sandbox tier correction','auditedCorrection':'Sandbox score incorrectly changed target tier; restored via authorized tier API. Original QUALIFIED tier and lifecycle restored; optimistic version and audit history were retained. Sandbox score master-tier sync disabled in final source.','businessColumnsSHA256':hashlib.sha256(current.encode()).hexdigest(),'originalSupplierIds':len(ids)}
(root/'docs/review/W20-22-supplier-preservation.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n');print('PASS: original supplier business columns verified against pre-P7 DB backup; audited classification correction retained without resetting versions.')
