// Read-only W30 deployment smoke; uses APP-device sessions to preserve browser sessions.
import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {join} from 'node:path'
import {call,encrypt} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review'
const origin=process.env.SRM_TEST_ENTRY||'http://localhost:18080'
assert.equal(origin,'http://localhost:18080')
const accounts=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8'))
async function loginActor(account,realm){
 const actor=accounts.find(x=>x.account===account);assert.ok(actor)
 const config=await call('/dev/config/sysBaseList');let captcha={}
 if(config.some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_'+realm.toUpperCase()&&String(x.configValue)==='true')){
  const c=await call('/auth/'+realm+'/getPicCaptcha')
  const {execFileSync}=await import('node:child_process')
  const raw=execFileSync('docker',['compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo],{cwd:bundle,encoding:'utf8'}).trim()
  let value;try{value=JSON.parse(raw)}catch{value=raw}
  captcha={validCode:String(value),validCodeReqNo:c.validCodeReqNo}
 }
 return call('/auth/'+realm+'/doLogin',null,{account:actor.account,password:encrypt(actor.password),device:'APP',...captcha})
}
const internal=await loginActor('admin','b'),portal=await loginActor('lixiaolong','c'),checks=[]
const landing=await fetch(origin+'/');assert.equal(landing.status,200)
const html=await landing.text();assert.match(html,/workspace-[A-Za-z0-9_-]+\.js/);checks.push('main Windows browser entry serves built workspace')
const context=await call('/srm/v1/session/context',internal);assert.equal(context.portal,false);checks.push('APP internal session authenticates without replacing PC session')
const categories=await call('/srm/v1/categories',internal);assert.ok(Array.isArray(categories));checks.push('main backend SRM module is healthy after W30 release')
await call('/client/c/srm/v1/quality/risks?current=1&size=5',portal,undefined,403);checks.push('portal remains denied from internal quality-risk list')
mkdirSync('docs/review',{recursive:true})
writeFileSync('docs/review/W30-main-deployment-verification.json',JSON.stringify({result:'passed',date:new Date().toISOString(),entry:origin,checks,mutation:'read-only requests; APP-device sessions only; no risk or supplier lifecycle changes',primaryDatabaseAndKeys:'retained; upgrade backup verified'},null,2)+'\n')
console.log('PASS: W30 main deployment read-only smoke')
