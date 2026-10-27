import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import crypto from 'node:crypto'
import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
const require=createRequire(new URL('../snowy-admin-web/package.json',import.meta.url))
const {sm2,sm3}=require('sm-crypto')
const source=readFileSync('snowy-admin-web/src/utils/smCrypto.js','utf8')
const publicKey=source.match(/'([0-9a-f]{130})'/)[1]
export const encrypt=p=>sm2.doEncrypt(p,publicKey,1)
export const compose=['compose','--env-file','.runtime/local.env','-f','deploy/compose.yml']
export function sql(query){return execFileSync('docker',[...compose,'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names snowy'],{input:query,encoding:'utf8',maxBuffer:16*1024*1024}).trim()}
const quote=v=>"'"+String(v).replaceAll('\\','\\\\').replaceAll("'","''")+"'"
const fixturePath='.runtime/w36-fixture.json'
export async function call(path,token,body,expected=200,raw=false){
 const options={headers:token?{token}:{},method:body===undefined?'GET':'POST'}
 if(body instanceof FormData)options.body=body
 else if(body!==undefined){options.body=JSON.stringify(body);options.headers['Content-Type']='application/json'}
 const response=await fetch('http://127.0.0.1:8080'+path,options)
 if(raw){assert.ok(response.ok);return new Uint8Array(await response.arrayBuffer())}
 const result=await response.json();assert.equal(result.code,expected,`${path}: expected ${expected}, got ${result.code} (${result.msg})`);return result.data
}
export async function captcha(){const c=await call('/auth/c/getPicCaptcha');const raw=execFileSync('docker',[...compose,'exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo],{encoding:'utf8'}).trim();let code;try{code=JSON.parse(raw)}catch{code=raw}return {validCode:String(code),validCodeReqNo:c.validCodeReqNo}}
export async function login(actor,realm='b'){return call('/auth/'+realm+'/doLogin',null,{account:actor.account,password:encrypt(actor.password),device:'PC',...(realm==='c'?await captcha():{})})}
const iid=()=>String(BigInt(Date.now())*1000000n+BigInt(crypto.randomInt(1,999999)))
export function createActor(name,orgId,roleCodes,scope){
 const uid=iid(),account='w36_'+name+'_'+crypto.randomBytes(4).toString('hex'),password=crypto.randomBytes(18).toString('base64url')+'A9!'
 sql(`INSERT INTO SYS_USER(ID,ACCOUNT,PASSWORD,NAME,USER_STATUS,ORG_ID,DELETE_FLAG,CREATE_TIME) VALUES(${quote(uid)},${quote(account)},${quote(sm3(password))},${quote('W36测试-'+name)},'ENABLE',${quote(orgId)},'NOT_DELETE',NOW());`)
 for(const code of roleCodes){let rid=sql(`SELECT ID FROM SYS_ROLE WHERE CODE=${quote(code)} AND DELETE_FLAG='NOT_DELETE' LIMIT 1;`);if(!rid){rid=iid();sql(`INSERT INTO SYS_ROLE(ID,NAME,CODE,CATEGORY,DELETE_FLAG,CREATE_TIME) VALUES(${quote(rid)},${quote(code)},${quote(code)},'GLOBAL','NOT_DELETE',NOW());`)}sql(`INSERT INTO SYS_RELATION(ID,OBJECT_ID,TARGET_ID,CATEGORY) VALUES(${quote(iid())},${quote(uid)},${quote(rid)},'SYS_USER_HAS_ROLE');`)}
 sql(`INSERT INTO SYS_RELATION(ID,OBJECT_ID,TARGET_ID,CATEGORY,EXT_JSON) VALUES(${quote(iid())},${quote(uid)},'/srm/v1/**','SYS_USER_HAS_PERMISSION',${quote(JSON.stringify({scopeCategory:scope}))});`)
 return {uid,account,password,orgId}
}
export function credit(prefix){const chars='0123456789ABCDEFGHJKLMNPQRTUWXY',w=[1,3,9,27,19,26,16,17,20,29,25,13,8,24,10,30,28];assert.equal(prefix.length,17);return prefix+chars[(31-[...prefix].reduce((s,c,i)=>s+chars.indexOf(c)*w[i],0)%31)%31]}
async function run(){
 mkdirSync('.runtime',{recursive:true});
 assert.ok(!sql("SELECT ID FROM SYS_USER WHERE ACCOUNT LIKE 'w36_a_%' LIMIT 1;"),'Previous fixture exists; retain evidence and clean it before a new run')
 const orgA=iid(),orgB=iid();sql(`INSERT INTO SYS_ORG(ID,PARENT_ID,NAME,CODE,CATEGORY,DELETE_FLAG,CREATE_TIME) VALUES(${quote(orgA)},'0','W36测试华东',${quote('W36A'+orgA)},'COMPANY','NOT_DELETE',NOW()),(${quote(orgB)},'0','W36测试华南',${quote('W36B'+orgB)},'COMPANY','NOT_DELETE',NOW());`)
 const f={orgA,orgB,a:createActor('a',orgA,['SUP-ADMIN','CAT-MGR'],'SCOPE_ORG'),b:createActor('b',orgB,['SUP-ADMIN'],'SCOPE_ORG'),reader:createActor('reader',orgA,['SQE'],'SCOPE_ORG'),self:createActor('self',orgA,['SUP-ADMIN'],'SCOPE_SELF'),root:createActor('root',orgA,['superAdmin'],'SCOPE_ALL')}
 writeFileSync(fixturePath,JSON.stringify(f,null,2),{mode:0o600})
 const a=await login(f.a),b=await login(f.b),reader=await login(f.reader),self=await login(f.self),root=await login(f.root)
 const checks=[];const pass=n=>{checks.push(n);console.log('PASS:',n)}
 await call('/srm/v1/suppliers',null,undefined,401);pass('unauthenticated_denied')
 const cc=credit('91110000ABCDEFGH1'),cc2=credit('91110000ABCDEFGH2')
 await call('/srm/v1/suppliers',a,{name:'错误信用代码',category:'MACHINING',creditCode:'IIIIIIIIIIIIIIIIII'},400);pass('credit_checksum_validation')
 const body={name:'W36真实档案测试有限公司',category:'MACHINING',creditCode:cc,bankAccount:'6222000012345678',bankName:'开发测试银行',address:'测试园区'}
 const s=await call('/srm/v1/suppliers',a,{...body,orgId:orgB,status:'QUALIFIED'});f.supplier=s.id;writeFileSync(fixturePath,JSON.stringify(f,null,2),{mode:0o600});assert.equal(s.orgId,orgA);assert.equal(s.status,'POTENTIAL');assert.ok(!s.creditCode.includes(cc));pass('trusted_scope_and_initial_state')
 await call('/srm/v1/suppliers',a,body,409);pass('same_org_credit_unique')
 const other=await call('/srm/v1/suppliers',b,{...body,name:'W36另一组织测试有限公司'});f.other=other.id;writeFileSync(fixturePath,JSON.stringify(f,null,2),{mode:0o600});pass('different_org_same_credit_allowed');
 await call('/srm/v1/suppliers',a,{...body,name:'A-W36排序样例有限公司',creditCode:cc2});
 assert.equal((await call('/srm/v1/suppliers?sortBy=name&sortOrder=ASC',a)).records[0].name,'A-W36排序样例有限公司');
 assert.equal((await call('/srm/v1/suppliers?orgId='+orgB,a)).total,0);await call('/srm/v1/suppliers?sortBy=NAME%3BDROP',a,undefined,400);pass('sorting_and_org_filter_cannot_expand_scope');
 assert.equal((await call('/srm/v1/suppliers',b)).records.length,1)
 await call('/srm/v1/suppliers/'+s.id,b,undefined,404);await call('/srm/v1/suppliers/'+s.id+'/edit',b,{...body,rowVersion:0},404);pass('cross_org_read_write_denied')
 assert.equal((await call('/srm/v1/suppliers',self)).total,0);pass('self_scope_not_org_scope')
 await call('/srm/v1/suppliers',reader,body,403);pass('read_role_cannot_write')
 const updated=await call('/srm/v1/suppliers/'+s.id+'/edit',a,{name:body.name+'（已更新）',category:body.category,address:'新测试地址',rowVersion:s.rowVersion});assert.equal(updated.rowVersion,1)
 await call('/srm/v1/suppliers/'+s.id+'/edit',a,{name:body.name,category:body.category,rowVersion:0},409);pass('optimistic_version_conflict')
 assert.equal((await call('/srm/v1/suppliers?searchKey='+encodeURIComponent('已更新'),a)).total,1);pass('search_and_reload_persistence')
 const ciphertext=sql(`SELECT CONCAT(CREDIT_CODE,'|',BANK_ACCOUNT) FROM SRM_SUPPLIER WHERE ID=${quote(s.id)};`);assert.ok(ciphertext.startsWith('v1.'));assert.ok(!ciphertext.includes(cc));assert.ok(!ciphertext.includes(body.bankAccount));pass('sm4_encrypted_at_rest')
 const pdf=new Blob(['%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF'],{type:'application/pdf'})
 const data=new FormData();data.append('file',pdf,'test.pdf');const attachment=await call('/srm/v1/suppliers/'+s.id+'/attachments',a,data)
 await call('/srm/v1/attachments/'+attachment.fileId,b,undefined,404);pass('attachment_scope_denied')
 const bytes=await call('/srm/v1/attachments/'+attachment.fileId,a,undefined,200,true);assert.equal(new TextDecoder().decode(bytes).slice(0,5),'%PDF-');pass('private_attachment_roundtrip')
 const cert={certType:'BUSINESS_LICENSE',certNo:'W36-TEST-01',validFrom:'2026-01-01',validTo:'2027-01-01',fileId:attachment.fileId}
 await call('/srm/v1/suppliers/'+other.id+'/certificates',b,cert,403);pass('foreign_attachment_not_reusable')
 await call('/srm/v1/suppliers/'+s.id+'/certificates',a,cert);let detail=await call('/srm/v1/suppliers/'+s.id,a);assert.deepEqual(detail.qualification.missing,['ISO9001'])
 await call('/srm/v1/suppliers/'+s.id+'/certificates',a,{...cert,validTo:'2028-01-01',previousId:detail.certificates[0].id})
 detail=await call('/srm/v1/suppliers/'+s.id,a);assert.equal(detail.certificates.length,2);assert.ok(detail.certificates.some(x=>x.status==='VOID'));assert.ok(detail.certificates.some(x=>x.version===2));pass('certificate_renewal_retains_history')
 const contact={name:'W36管理员',phone:'13800138000',email:'test@example.invalid',roles:['ADMIN'],enabled:true,rowVersion:0}
 await call('/srm/v1/suppliers/'+s.id+'/contacts',a,{...contact,roles:['QUOTE']},409)
 await call('/srm/v1/suppliers/'+s.id+'/contacts',a,contact);detail=await call('/srm/v1/suppliers/'+s.id,a);const c=detail.contacts[0];assert.ok(c.phone.includes('****'))
 await call('/srm/v1/suppliers/'+s.id+'/contacts/'+c.id+'/access',a,{roles:['QUOTE'],enabled:true,rowVersion:c.rowVersion},409);pass('first_and_last_admin_protection')
 const portal={account:'w36_portal_'+crypto.randomBytes(4).toString('hex'),password:crypto.randomBytes(9).toString('base64url')+'A9!'}
 await call('/srm/v1/suppliers/'+s.id+'/contacts/'+c.id+'/account',a,{account:portal.account,passwordCipher:encrypt(portal.password)});f.portal=portal;f.contact=c.id;writeFileSync(fixturePath,JSON.stringify(f,null,2),{mode:0o600})
 const pt=await login(portal,'c');const pc=await call('/client/c/srm/v1/session/context',pt);assert.equal(pc.supplierId,s.id)
 assert.equal((await call('/client/c/srm/v1/suppliers',pt)).total,1);await call('/client/c/srm/v1/suppliers/'+other.id,pt,undefined,404);pass('real_portal_login_and_supplier_isolation')
 await call('/srm/v1/suppliers/'+s.id+'/contacts',a,{...contact,name:'W36第二管理员',phone:'13900139000'})
 detail=await call('/srm/v1/suppliers/'+s.id,a);const before=detail.contacts.find(x=>x.id===c.id)
 await call('/srm/v1/suppliers/'+s.id+'/contacts/'+c.id+'/access',a,{roles:['ADMIN'],enabled:false,rowVersion:before.rowVersion})
 await call('/client/c/srm/v1/session/context',pt,undefined,401);pass('disabled_contact_revokes_existing_session');
 const disabledLogin=await fetch('http://127.0.0.1:8080/auth/c/doLogin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({account:portal.account,password:encrypt(portal.password),device:'PC',...await captcha()})});assert.notEqual((await disabledLogin.json()).code,200);pass('disabled_contact_cannot_login_again');
 detail=await call('/srm/v1/suppliers/'+s.id,a);const disabled=detail.contacts.find(x=>x.id===c.id)
 await call('/srm/v1/suppliers/'+s.id+'/contacts/'+c.id+'/access',a,{roles:['ADMIN'],enabled:true,rowVersion:disabled.rowVersion})
 await login(portal,'c');pass('re_enabled_contact_can_login')
 // Concurrent attempts to disable both administrators must serialize on the supplier aggregate.
 detail=await call('/srm/v1/suppliers/'+s.id,a);const admins=detail.contacts.filter(x=>x.enabled&&x.roles.includes('ADMIN'));
 const results=await Promise.all(admins.map(async c=>{const r=await fetch('http://127.0.0.1:8080/srm/v1/suppliers/'+s.id+'/contacts/'+c.id+'/access',{method:'POST',headers:{token:a,'Content-Type':'application/json'},body:JSON.stringify({roles:c.roles,enabled:false,rowVersion:c.rowVersion})});return (await r.json()).code}));
 assert.deepEqual(results.sort(),[200,409]);detail=await call('/srm/v1/suppliers/'+s.id,a);
 assert.equal(detail.contacts.filter(x=>x.enabled&&x.roles.includes('ADMIN')).length,1);
 for(const c of detail.contacts)if(!c.enabled)await call('/srm/v1/suppliers/'+s.id+'/contacts/'+c.id+'/access',a,{roles:c.roles,enabled:true,rowVersion:c.rowVersion});pass('concurrent_last_admin_protection');
 detail=await call('/srm/v1/suppliers/'+s.id,a);const secondContact=detail.contacts.find(x=>x.id!==c.id);
 const portal2={account:'w36_second_'+crypto.randomBytes(4).toString('hex'),password:crypto.randomBytes(9).toString('base64url')+'A9!'};
 await call('/srm/v1/suppliers/'+s.id+'/contacts/'+secondContact.id+'/account',a,{account:portal2.account,passwordCipher:encrypt(portal2.password)});f.portal2=portal2;writeFileSync(fixturePath,JSON.stringify(f,null,2),{mode:0o600});
 const pt1=await login(portal,'c'),pt2=await login(portal2,'c');assert.equal((await call('/client/c/srm/v1/session/context',pt1)).supplierId,s.id);assert.equal((await call('/client/c/srm/v1/session/context',pt2)).supplierId,s.id);pass('independent_multi_contact_accounts');
 detail=await call('/srm/v1/suppliers/'+s.id,a);const liveSecond=detail.contacts.find(x=>x.id===secondContact.id);
 await call('/srm/v1/suppliers/'+s.id+'/contacts/'+secondContact.id+'/access',a,{roles:['QUOTE'],enabled:true,rowVersion:liveSecond.rowVersion});
 assert.equal((await call('/client/c/srm/v1/session/context',pt2)).canManage,false);await call('/client/c/srm/v1/suppliers/'+s.id+'/contacts',pt2,contact,403);assert.ok((await call('/client/c/srm/v1/suppliers/'+s.id,pt2)).bankAccount.includes('****'));pass('portal_roles_refresh_and_field_masking');
 const excel=await call('/srm/v1/suppliers-export',a,undefined,200,true);assert.equal(excel[0],80);assert.equal(excel[1],75);writeFileSync('.runtime/w36-export.xlsx',excel);pass('real_xlsx_export')
 await call('/srm/v1/suppliers/'+s.id+'/tier',a,{tier:'PREFERRED',rowVersion:1})
 await call('/srm/v1/suppliers/'+s.id+'/tier',reader,{tier:'STRATEGIC',rowVersion:2},403);pass('tier_role_permission')
 detail=await call('/srm/v1/suppliers/'+s.id,a);assert.ok(detail.events.length>5);assert.ok(detail.events.every(x=>x.signatureValid));pass('signed_business_history');
 const event=detail.events[0];const eventTime=sql(`SELECT CREATED_AT FROM SRM_BUSINESS_EVENT WHERE ID=${quote(event.id)};`);
 sql(`UPDATE SRM_BUSINESS_EVENT SET CREATED_AT=DATE_ADD(CREATED_AT,INTERVAL 1 SECOND) WHERE ID=${quote(event.id)};`);
 try{const tampered=await call('/srm/v1/suppliers/'+s.id,a);assert.ok(tampered.events.some(e=>e.id===event.id&&!e.signatureValid));}finally{sql(`UPDATE SRM_BUSINESS_EVENT SET CREATED_AT=${quote(eventTime)} WHERE ID=${quote(event.id)};`)}pass('audit_timestamp_tampering_detected');
 // Expiry uses actual Shanghai business date, not a hardcoded fixture year.
 const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())
 await call('/srm/v1/suppliers/'+s.id+'/certificates',a,{certType:'ISO9001',certNo:'W36-EXPIRY',validFrom:'2026-01-01',validTo:today,fileId:attachment.fileId})
 await call('/srm/v1/maintenance/certificate-warnings',root,{})
 const n=(await call('/srm/v1/alerts',a)).length;await call('/srm/v1/maintenance/certificate-warnings',root,{})
 assert.equal((await call('/srm/v1/alerts',a)).length,n);assert.ok(n>=1);pass('expiry_scan_deduplication');
 assert.equal(sql(`SELECT COUNT(*) FROM DEV_RELATION r JOIN DEV_MESSAGE m ON m.ID=r.OBJECT_ID WHERE r.TARGET_ID=${quote(f.self.uid)} AND m.CONTENT LIKE '%${s.id}%';`),'0');pass('warning_respects_self_scope');
 assert.equal(sql(`SELECT STORAGE_ENGINE FROM SRM_ATTACHMENT_SCOPE WHERE FILE_ID=${quote(attachment.fileId)};`),'MINIO');
 const anon=await fetch('http://127.0.0.1:19000/srm-private/'+attachment.fileId+'.blob');assert.equal(anon.status,403);pass('real_private_minio_storage');
 assert.equal(sql(`SELECT COUNT(*) FROM DEV_LOG WHERE NAME LIKE 'SRM%' AND (PARAM_JSON LIKE '%${body.bankAccount}%' OR RESULT_JSON LIKE '%${cc}%');`),'0');pass('sensitive_data_not_logged')
 f.aToken=a;writeFileSync(fixturePath,JSON.stringify(f,null,2),{mode:0o600})
 mkdirSync('docs/workspace',{recursive:true});writeFileSync('docs/workspace/api-verification.json',JSON.stringify({date:today,result:'passed',checks},null,2)+'\n')
 console.log('All',checks.length,'API checks passed. Temporary fixture credentials are stored only in ignored .runtime/.')
}
if(process.argv[1]?.endsWith('test-workspace-api.mjs'))run().catch(e=>{console.error(e.message);process.exitCode=1})
