import {readFileSync,unlinkSync,existsSync} from 'node:fs'
import crypto from 'node:crypto'
import {sql,login,call} from './test-workspace-api.mjs'
const path='.runtime/w36-fixture.json'
if(existsSync(path)){
 const f=JSON.parse(readFileSync(path,'utf8'));for(const org of [f.orgA,f.orgB]){if(!/^[0-9]{13,20}$/.test(org))throw new Error('Invalid fixture id');if(sql(`SELECT NAME FROM SYS_ORG WHERE ID='${org}';`).startsWith('W36测试')!==true)throw new Error('Fixture organization ownership check failed')}
 for(const key of ['a','b','reader','self','root']){try{const t=await login(f[key]);await call('/auth/b/doLogout',t)}catch{}}
 if(f.portal&&f.contact&&f.supplier){try{const t=await login(f.a);const d=await call('/srm/v1/suppliers/'+f.supplier,t);const c=d.contacts.find(x=>x.id===f.contact);if(c?.enabled)await call('/srm/v1/suppliers/'+f.supplier+'/contacts/'+c.id+'/access',t,{roles:c.roles,enabled:false,rowVersion:c.rowVersion});await call('/auth/b/doLogout',t)}catch{}}
 for(const key of ['portal','portal2'])if(f[key]){try{const t=await login(f[key],'c');await call('/auth/c/doLogout',t)}catch{}}
 const rt=await login(f.root);for(const aid of sql(`SELECT ID FROM SRM_APPROVAL_LINK WHERE ORG_ID IN ('${f.orgA}','${f.orgB}') AND STATUS='PENDING';`).split('\n').filter(Boolean))await call('/srm/v1/approvals/'+aid+'/withdraw',rt,{comment:'清理本脚本创建的开发验收夹具'});await call('/auth/b/doLogout',rt);
 const orgs=`'${f.orgA}','${f.orgB}'`,uids=[...['a','b','reader','self','root'].map(k=>f[k].uid),...(f.w79Actors||[]).map(a=>a.uid)].map(id=>`'${id}'`).join(',')
 const accountIds=sql(`SELECT c.ACCOUNT_ID FROM SRM_SUPPLIER_CONTACT c WHERE c.ORG_ID IN (${orgs}) AND c.ACCOUNT_ID IS NOT NULL;`).split('\n').filter(Boolean)
 const files=sql(`SELECT CONCAT(FILE_ID,':',STORAGE_ENGINE) FROM SRM_ATTACHMENT_SCOPE WHERE ORG_ID IN (${orgs});`).split('\n').filter(Boolean)
 const cleanupSql=`DELETE FROM SRM_APPROVAL_ACTION WHERE APPROVAL_ID IN (SELECT ID FROM SRM_APPROVAL_LINK WHERE ORG_ID IN (${orgs}));DELETE FROM SRM_PROFILE_VERSION WHERE ORG_ID IN (${orgs});DELETE FROM SRM_REGISTRATION_INVITE WHERE ORG_ID IN (${orgs});DELETE FROM SRM_EVENT_OUTBOX WHERE ORG_ID IN (${orgs});DELETE FROM SRM_CERT_ALERT WHERE ORG_ID IN (${orgs});DELETE FROM SRM_BUSINESS_EVENT WHERE ORG_ID IN (${orgs});DELETE FROM SRM_PORTAL_ACCOUNT_RESERVATION WHERE CONTACT_ID IN (SELECT ID FROM SRM_SUPPLIER_CONTACT WHERE ORG_ID IN (${orgs}));DELETE FROM SRM_ATTACHMENT_SCOPE WHERE ORG_ID IN (${orgs});DELETE FROM SRM_APPROVAL_LINK WHERE ORG_ID IN (${orgs});DELETE FROM SRM_SUPPLIER_CERT WHERE ORG_ID IN (${orgs});DELETE FROM SRM_SUPPLIER_CONTACT WHERE ORG_ID IN (${orgs});DELETE FROM SRM_SUPPLIER WHERE ORG_ID IN (${orgs});DELETE FROM SYS_RELATION WHERE OBJECT_ID IN (${uids});DELETE FROM SYS_USER_EXT WHERE USER_ID IN (${uids});DELETE FROM SYS_USER WHERE ID IN (${uids});DELETE FROM SYS_ORG WHERE ID IN (${orgs});`

 for(const entry of files){
  const [id,engine]=entry.split(':');if(!/^[0-9]{1,20}$/.test(id))continue;
  if(engine==='LOCAL'){const p='.runtime/srm-files/'+id+'.blob';if(existsSync(p))unlinkSync(p)}
  if(engine==='MINIO'){
   const v=Object.fromEntries(readFileSync('.runtime/storage.env','utf8').split('\n').filter(x=>x.includes('=')).map(x=>{const i=x.indexOf('=');return[x.slice(0,i),x.slice(i+1)]}));
   if(v.SRM_MINIO_ENDPOINT!=='http://127.0.0.1:19000')continue;
   const endpoint=new URL(v.SRM_MINIO_ENDPOINT),date=new Date().toISOString().replace(/[:-]|\.\d{3}/g,''),day=date.slice(0,8),scope=day+'/us-east-1/s3/aws4_request';
   const hash=x=>crypto.createHash('sha256').update(x).digest('hex'),hmac=(k,x)=>crypto.createHmac('sha256',k).update(x).digest();
   const uri='/'+v.SRM_MINIO_BUCKET+'/'+id+'.blob',payload=hash('');const signed='host;x-amz-content-sha256;x-amz-date';
   const canonical='DELETE\n'+uri+'\n\n'+'host:'+endpoint.host+'\nx-amz-content-sha256:'+payload+'\nx-amz-date:'+date+'\n\n'+signed+'\n'+payload;
   const signingKey=hmac(hmac(hmac(hmac('AWS4'+v.SRM_MINIO_SECRET_KEY,day),'us-east-1'),'s3'),'aws4_request');
   const signature=crypto.createHmac('sha256',signingKey).update('AWS4-HMAC-SHA256\n'+date+'\n'+scope+'\n'+hash(canonical)).digest('hex');
   const response=await fetch(endpoint.origin+uri,{method:'DELETE',headers:{'x-amz-content-sha256':payload,'x-amz-date':date,Authorization:'AWS4-HMAC-SHA256 Credential='+v.SRM_MINIO_ACCESS_KEY+'/'+scope+', SignedHeaders='+signed+', Signature='+signature}});
   if(response.status!==204)throw new Error('Owned test object cleanup failed with HTTP '+response.status);
  }
 }
 sql(cleanupSql);
 for(const id of accountIds){if(!/^[0-9]{1,20}$/.test(id))throw new Error('Invalid account fixture');sql(`DELETE FROM CLIENT_USER_EXT WHERE USER_ID='${id}';DELETE FROM CLIENT_USER WHERE ID='${id}' AND ACCOUNT LIKE 'w36_%';`)}
 unlinkSync(path);console.log('Cleaned owned W36 test fixture; unrelated business data retained.')
}
