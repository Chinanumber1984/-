#!/usr/bin/env python3
"""Non-destructive local review backups and recovery into a NEW isolated Docker project."""
from pathlib import Path
import argparse,datetime,gzip,hashlib,json,os,re,shutil,subprocess,sys,tarfile,time
ROOT=Path(__file__).resolve().parents[1]
NODE='node:22-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914'
def run(cmd,**kw):return subprocess.run(cmd,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,**kw)
def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def compose(bundle,project=None,config=None):return ['docker','compose','--project-directory',str(bundle),'--env-file',str(bundle/'runtime/compose.env'),'-f',str(config or bundle/'compose.yml')]+(['-p',project]if project else [])
def sql(c,text):return run(c+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot --batch --skip-column-names snowy'],input=text.encode()).stdout.decode().strip()
def checksums(c):
 names=sql(c,"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='snowy' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME").splitlines()
 if not all(re.fullmatch(r'[A-Za-z0-9_$]+',n) for n in names):raise ValueError('Unexpected database table identifier')
 data=sql(c,'CHECKSUM TABLE '+','.join('`'+n+'`' for n in names)+';')
 return dict(line.split('\t') for line in data.splitlines())
def volume(c,service,target):
 cid=run(c+['ps','-q',service]).stdout.decode().strip()
 if not cid:raise ValueError('Required source service is not running: '+service)
 mounts=json.loads(run(['docker','inspect','--format','{{json .Mounts}}',cid]).stdout)
 return next(m['Name'] for m in mounts if m['Destination']==target and m['Type']=='volume')
def volume_archive(name,path):
 with path.open('wb') as out:subprocess.run(['docker','run','--rm','-v',name+':/source:ro',NODE,'tar','-czf','-','-C','/source','.'],stdout=out,stderr=subprocess.PIPE,check=True)
def validate_tar(path):
 with tarfile.open(path,'r:gz') as t:
  for m in t:
   p=Path(m.name)
   if p.is_absolute() or '..' in p.parts or m.issym() or m.islnk() or not(m.isfile() or m.isdir()):raise ValueError('Unsafe archive member')
def verify(path):
 manifest=json.loads((path/'manifest.json').read_text())
 if manifest.get('version')!=1:raise ValueError('Unsupported backup format')
 expected={'database.sql.gz','bundle.tar.gz','files.tar.gz','sandbox.tar.gz'}
 if set(manifest['archives'])!=expected:raise ValueError('Unexpected backup archive set')
 for name,data in manifest['archives'].items():
  if sha(path/name)!=data['sha256']:raise ValueError('Backup checksum mismatch: '+name)
  if name=='database.sql.gz':
   with gzip.open(path/name,'rb') as f:
    while f.read(1024*1024):pass
  else:validate_tar(path/name)
 return manifest
def backup(bundle,out):
 if out.exists():raise ValueError('Existing backup retained; choose a new output directory')
 out.mkdir(parents=True,mode=0o700);c=compose(bundle);started=time.monotonic();before=checksums(c)
 with (out/'dump-stderr.log').open('wb') as log,gzip.open(out/'database.sql.gz','wb',compresslevel=1) as zipped:
  p=subprocess.Popen(c+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --events --triggers --no-tablespaces --skip-lock-tables --set-gtid-purged=OFF snowy'],stdout=subprocess.PIPE,stderr=log)
  shutil.copyfileobj(p.stdout,zipped);p.stdout.close()
  if p.wait()!=0:raise ValueError('Database backup failed; private diagnostics retained')
 snapshot_utc=datetime.datetime.now(datetime.timezone.utc).isoformat()
 with tarfile.open(out/'bundle.tar.gz','w:gz',compresslevel=1) as t:
  for p in sorted(bundle.iterdir()):
   if p.name not in {'app-log','node_modules'}:t.add(p,arcname=p.name)
 volume_archive(volume(c,'backend','/app/files'),out/'files.tar.gz')
 volume_archive(volume(c,'collab-sandbox','/data'),out/'sandbox.tar.gz')
 after=checksums(c);stable={k:v for k,v in before.items()if v!='NULL' and after.get(k)==v}
 credentials={str(p.relative_to(bundle)):sha(p)for p in (bundle/'runtime').rglob('*')if p.is_file()}
 manifest={'version':1,'createdAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'databaseSnapshotCompletedAt':snapshot_utc,'sourceBundle':str(bundle),'sourceProject':'srm-review','archives':{n:{'sha256':sha(out/n),'bytes':(out/n).stat().st_size}for n in ['database.sql.gz','bundle.tar.gz','files.tar.gz','sandbox.tar.gz']},'stableTables':stable,'tablesChangedDuringBackup':sorted(set(before)-set(stable)),'runtimeHashes':credentials,'seconds':round(time.monotonic()-started,3),'sourceServicesStopped':False,'sourceVolumesModified':False,'consistency':'MySQL transactional snapshot; file and local sandbox volumes copied afterwards. No claim of coordinated real-vendor zero-RPO backup.'}
 (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');verify(out);print('PASS: database, application, runtime, attachments and sandbox backup verified. '+str(out))
 return manifest
def file_proof(path):
 values={}
 with tarfile.open(path,'r:gz') as t:
  for m in t:
   if m.isfile():values[str(Path(m.name))]=hashlib.sha256(t.extractfile(m).read()).hexdigest()
 return values
def restore(backup_path,dest,project,port):
 started=time.monotonic();manifest=verify(backup_path)
 if project=='srm-review' or not re.fullmatch(r'srm-drill-[a-z0-9-]{1,40}',project):raise ValueError('Recovery must use a NEW srm-drill-* project, never srm-review')
 if port==18080 or not 1024<=port<=65535:raise ValueError('Recovery must use a separate local port')
 if dest.exists():raise ValueError('Existing recovery directory retained; choose a new directory')
 if run(['docker','ps','-aq','--filter','label=com.docker.compose.project='+project]).stdout.strip():raise ValueError('Recovery project already exists; no data overwritten')
 for name in ['mysql-data','files','sandbox-data']:
  if run(['docker','volume','ls','-q','--filter','name=^'+project+'_'+name+'$']).stdout.strip():raise ValueError('Recovery volume already exists; no data overwritten')
 dest.mkdir(parents=True,mode=0o700);bundle=dest/'bundle';bundle.mkdir(mode=0o755)
 with tarfile.open(backup_path/'bundle.tar.gz','r:gz') as t:t.extractall(bundle,filter='data')
 for name,value in manifest['runtimeHashes'].items():
  if sha(bundle/name)!=value:raise ValueError('Restored runtime/secret file differs: '+name)
 source=Path(manifest['sourceBundle']);cfg=json.loads(run(compose(bundle)+['config','--format','json']).stdout);cfg['name']=project
 # Keep application services on an internal bridge. The gateway also joins a
 # dedicated ingress bridge so Docker can publish the browser port to WSL.
 # No backend service is connected to that ingress bridge.
 cfg['networks']={'default':{'name':project+'_default','internal':True},'ingress':{'name':project+'_ingress','internal':False}}
 for name in cfg['volumes']:cfg['volumes'][name]={'name':project+'_'+name}
 for service in cfg['services'].values():
  for mount in service.get('volumes',[]):
   if mount['type']=='bind':
    path=Path(mount['source'])
    if path.is_relative_to(source):mount['source']=str(bundle/path.relative_to(source))
  service['networks']={'default':None}
 cfg['services']['gateway']['networks']={'default':None,'ingress':None}
 cfg['services']['mysql']['volumes']=[m for m in cfg['services']['mysql']['volumes']if m['target']!='/docker-entrypoint-initdb.d']
 cfg['services']['mysql']['environment']={'MYSQL_DATABASE':'snowy','MYSQL_ROOT_PASSWORD':'${SRM_DB_PASSWORD:?Private runtime required}'}
 cfg['services']['backend'].pop('environment',None);cfg['services']['backend']['env_file']=[str(bundle/'runtime/backend.env')]
 cfg['services']['backend']['command']=[v.replace('-Xmx1536m','-Xmx768m')for v in cfg['services']['backend']['command']]
 cfg['services']['gateway']['ports']=[{'host_ip':'127.0.0.1','published':str(port),'target':5176,'protocol':'tcp'}]
 config=bundle/'compose.drill.json';config.write_text(json.dumps(cfg,indent=2)+'\n');(bundle/'compose.yml').write_text(json.dumps(cfg,indent=2)+'\n');c=compose(bundle,project,config)
 with (dest/'restore.log').open('wb') as log:
  subprocess.run(c+['up','-d','--wait','--wait-timeout','180','mysql','redis'],stdout=log,stderr=log,check=True)
  with gzip.open(backup_path/'database.sql.gz','rb') as data:
   p=subprocess.Popen(c+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot snowy'],stdin=subprocess.PIPE,stdout=log,stderr=log);shutil.copyfileobj(data,p.stdin);p.stdin.close()
   if p.wait()!=0:raise ValueError('Restore failed; isolated diagnostics retained; original instance untouched')
  recovered=checksums(c)
  for table,value in manifest['stableTables'].items():
   if recovered.get(table)!=value:raise ValueError('Restored stable table checksum differs: '+table)
  sql(c,"CREATE TABLE SRM_P8_DRILL_MARKER(ID int PRIMARY KEY,PROJECT varchar(80) NOT NULL);INSERT INTO SRM_P8_DRILL_MARKER VALUES(1,'"+project+"');")
  for key,archive in [('files','files.tar.gz'),('sandbox-data','sandbox.tar.gz')]:
   name=project+'_'+key;run(['docker','volume','create',name])
   with (backup_path/archive).open('rb') as data:subprocess.run(['docker','run','--rm','-i','-v',name+':/target',NODE,'tar','-xzf','-','-C','/target'],stdin=data,stdout=log,stderr=log,check=True)
  script="const fs=require('fs'),p=require('path'),c=require('crypto'),out={};function walk(d,r=''){for(const n of fs.readdirSync(d)){const f=p.join(d,n),k=p.join(r,n);if(fs.statSync(f).isDirectory())walk(f,k);else out[k]=c.createHash('sha256').update(fs.readFileSync(f)).digest('hex')}}walk('/data');console.log(JSON.stringify(out))"
  hashes=json.loads(run(['docker','run','--rm','-v',project+'_files:/data:ro',NODE,'node','-e',script]).stdout)
  expected=file_proof(backup_path/'files.tar.gz')
  if hashes!=expected:raise ValueError('Restored attachment content hashes differ')
  entry=bundle/'workspace/build/workspace.html';html=entry.read_text();banner='<div style="background:#fff0bf;color:#704d00;padding:12px;text-align:center;font:14px sans-serif">隔离备份恢复演练 · '+str(port)+' · 测试数据不写回原实例</div>';entry.write_text(html.replace('<body>','<body>'+banner))
  subprocess.run(c+['up','-d','--wait','--wait-timeout','360'],stdout=log,stderr=log,check=True)
 report={'result':'passed','project':project,'entry':'http://localhost:'+str(port)+'/','backup':str(backup_path),'restoredBundle':str(bundle),'composeFile':str(config),'stableTablesVerified':len(manifest['stableTables']),'tablesChangedDuringBackup':manifest['tablesChangedDuringBackup'],'attachmentFilesVerified':len(expected),'runtimeFilesVerified':len(manifest['runtimeHashes']),'seconds':round(time.monotonic()-started,3),'backendServicesHaveExternalRoute':False,'gatewayHasIngressRouteForPublishedBrowserPort':True,'originalInstanceModified':False,'recoveryPoint':'Backup transaction snapshot; later changes are not included; business RPO/RTO targets pending'}
 (dest/'recovery-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('PASS: recovery into new isolated project; database, attachments and keys verified. '+report['entry']);return report
if __name__=='__main__':
 os.umask(0o077);p=argparse.ArgumentParser(description=__doc__);sub=p.add_subparsers(dest='operation',required=True)
 b=sub.add_parser('backup');b.add_argument('--bundle',type=Path,default=ROOT/'artifacts/SRM-local-review');b.add_argument('--out',type=Path,required=True)
 v=sub.add_parser('verify');v.add_argument('backup',type=Path)
 r=sub.add_parser('restore');r.add_argument('backup',type=Path);r.add_argument('--dest',type=Path,required=True);r.add_argument('--project',required=True);r.add_argument('--port',type=int,default=18081)
 a=p.parse_args()
 try:
  if a.operation=='backup':backup(a.bundle.resolve(),a.out.resolve())
  elif a.operation=='verify':verify(a.backup.resolve());print('PASS: backup archive integrity verified')
  else:restore(a.backup.resolve(),a.dest.resolve(),a.project,a.port)
 except Exception as e:print('FAIL:',type(e).__name__,str(e)if not isinstance(e,subprocess.CalledProcessError)else 'Docker operation failed; private diagnostics retained',file=sys.stderr);sys.exit(1)
