import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {join} from 'node:path'
import {call,encrypt} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review',origin=process.env.SRM_TEST_ENTRY||'http://localhost:18081'
assert.equal(origin,'http://localhost:18081','Approval history regression must use the isolated review entry')
const recovery=JSON.parse(readFileSync(join(bundle,'..','recovery-result.json'),'utf8'));assert.equal(recovery.project,'srm-drill-p8-20261028')
const users=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8')),checks=[]
async function login(account,realm){const actor=users.find(x=>x.account===account);assert.ok(actor,`${account} test account exists`);const config=await call('/dev/config/sysBaseList');const key='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_'+realm.toUpperCase();let captcha={};if(config.some(x=>x.configKey===key&&String(x.configValue)==='true')){const c=await call('/auth/'+realm+'/getPicCaptcha');const args=['docker','compose','--project-directory',bundle,'--env-file',join(bundle,'runtime/compose.env'),'-f',join(bundle,'compose.drill.json'),'-p',recovery.project,'exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo];const raw=execFileSync(args[0],args.slice(1),{encoding:'utf8'}).trim();let code;try{code=JSON.parse(raw)}catch{code=raw}captcha={validCode:String(code),validCodeReqNo:c.validCodeReqNo}}return call('/auth/'+realm+'/doLogin',null,{account:actor.account,password:encrypt(actor.password),device:'PC',...captcha})}
const internal=await login('admin','b'),context=await call('/srm/v1/session/context',internal);assert.equal(context.portal,false)
const query=(current=1,size=100,status='',kind='')=>new URLSearchParams({current:String(current),size:String(size),status,kind})
const page=await call('/srm/v1/approvals/history?'+query(),internal)
assert.ok(page.records.every(r=>r.status!=='PENDING'))
for(const row of page.records){const detail=await call('/srm/v1/approvals/'+row.id,internal);assert.equal(detail.id,row.id);assert.equal(detail.supplierId,row.supplierId)}
checks.push('history list contains completed approvals only and every row opens a scope-checked detail')
if(page.records.length){const sample=page.records[0];const filtered=await call('/srm/v1/approvals/history?'+query(1,100,sample.status,sample.kind),internal);assert.ok(filtered.records.every(r=>r.status===sample.status&&r.kind===sample.kind));checks.push('status and business-type filters return matching history')}
await call('/srm/v1/approvals/history?'+query(1,20,'INVALID',''),internal,undefined,400);await call('/srm/v1/approvals/history?'+query(1,101),internal,undefined,400);checks.push('invalid status and oversized page are rejected')
const portal=await login('gysadmin','c');await call('/client/c/srm/v1/approvals/history?'+query(1,20),portal,undefined,403);checks.push('supplier portal cannot call internal approval history')
const report={result:'passed',entry:origin,project:recovery.project,date:new Date().toISOString(),checks,recordsVisibleToAdmin:page.records.length,mutation:'clone-only PC auth sessions and GET-only business queries; no approval or supplier rows changed'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W27-approval-history-api-verification.json',JSON.stringify(report,null,2)+'\n')
console.log(`PASS: ${checks.length} approval history API checks`)
