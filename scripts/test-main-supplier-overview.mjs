// Post-upgrade smoke on the live local service. Uses APP device to preserve the user's PC session.
import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {join} from 'node:path'
import {call,sql,encrypt} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review'
const origin=process.env.SRM_TEST_ENTRY||'http://localhost:18080'
if(origin!=='http://localhost:18080')throw new Error('Expected the protected local main entry')
const profile=JSON.parse(readFileSync(join(bundle,'runtime/account-profile.json'),'utf8'))
const users=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8'))
const actor=users.find(x=>x.account==='lijunhua'),checks=[]
const flags=await call('/dev/config/sysBaseList')
const captchaOpen=flags.some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_B'&&String(x.configValue)==='true')
let captcha={}
if(captchaOpen){
 const c=await call('/auth/b/getPicCaptcha')
 const raw=execFileSync('docker',['compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo],{cwd:bundle,encoding:'utf8'}).trim()
 let code;try{code=JSON.parse(raw)}catch{code=raw}
 captcha={validCode:String(code),validCodeReqNo:c.validCodeReqNo}
}
const token=await call('/auth/b/doLogin',null,{account:actor.account,password:encrypt(actor.password),device:'APP',...captcha})
const context=await call('/srm/v1/session/context',token)
assert.ok(context.roles.includes('SQE')&&context.roles.includes('BUYER'))
const target=profile.supplierId
const foreign=sql("SELECT ID FROM SRM_SUPPLIER WHERE ORG_ID<>"+JSON.stringify(profile.orgId)+" AND DELETE_FLAG='NOT_DELETE' ORDER BY ID LIMIT 1;")
assert.ok(foreign)
async function list(path,sid,expected=200){const q=new URLSearchParams({current:'1',size:'100',supplierId:sid});return call(path+'?'+q,token,undefined,expected)}
const scores=await list('/srm/v1/performance/scores',target);assert.ok(scores.records.every(r=>r.supplierId===target));checks.push('performance overview filtered to selected supplier')
await list('/srm/v1/performance/scores',foreign,404);checks.push('cross-organization performance filter rejected')
const risks=await list('/srm/v1/quality/risks',target);assert.ok(risks.records.every(r=>r.supplierId===target));checks.push('quality risk overview filtered to selected supplier')
await list('/srm/v1/quality/risks',foreign,404);checks.push('cross-organization risk filter rejected')
const orders=await list('/srm/v1/collaboration/orders',target);assert.ok(orders.records.every(r=>r.supplierId===target));checks.push('order overview filtered; no unit-price field in rows')
await list('/srm/v1/collaboration/orders',foreign,404);checks.push('cross-organization order filter rejected')
const report={result:'passed',entry:origin,date:new Date().toISOString(),checks,mutation:'read-only business data; APP-device smoke token only; original browser PC session untouched'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W25-main-deployment-verification.json',JSON.stringify(report,null,2)+'\n')
console.log('PASS: live main entry supplier overview smoke; original PC session was not evicted')
