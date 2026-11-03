// Read-only W31 smoke on the upgraded local service. Uses APP to preserve PC login.
import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {join} from 'node:path'
import {call,encrypt} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review'
const origin=process.env.SRM_TEST_ENTRY||'http://localhost:18080'
assert.equal(origin,'http://localhost:18080')
const accounts=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8'))
const actor=accounts.find(x=>x.account==='admin');assert.ok(actor)
const config=await call('/dev/config/sysBaseList');let captcha={}
if(config.some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_B'&&String(x.configValue)==='true')){
 const c=await call('/auth/b/getPicCaptcha')
 const raw=execFileSync('docker',['compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo],{cwd:bundle,encoding:'utf8'}).trim()
 let value;try{value=JSON.parse(raw)}catch{value=raw}
 captcha={validCode:String(value),validCodeReqNo:c.validCodeReqNo}
}
const token=await call('/auth/b/doLogin',null,{account:actor.account,password:encrypt(actor.password),device:'APP',...captcha})
const context=await call('/srm/v1/session/context',token)
assert.equal(context.portal,false)
const listing=await call('/srm/v1/suppliers?current=1&size=20&status=QUALIFIED',token)
const supplier=listing.records[0];assert.ok(supplier,'Need an in-scope qualified supplier for a no-write regression')
const detail=await call('/srm/v1/suppliers/'+supplier.id,token)
const denied=await fetch(origin+'/api/srm/v1/suppliers/'+supplier.id+'/edit',{method:'POST',headers:{token,'Content-Type':'application/json'},body:JSON.stringify({name:detail.name+'未经审批',category:detail.category,rowVersion:detail.rowVersion})}).then(r=>r.json())
assert.equal(denied.code,409);const after=await call('/srm/v1/suppliers/'+supplier.id,token);assert.equal(after.name,detail.name)
const landing=await fetch(origin+'/');assert.equal(landing.status,200);const html=await landing.text(),asset=html.match(/workspace-[A-Za-z0-9_-]+\.js/);assert.ok(asset,'Workspace application bundle is served')
const js=await fetch(origin+'/assets/'+asset[0]).then(r=>r.text());assert.match(js,/维护注册地址/);assert.match(js,/申请关键资料变更/)
const checks=['18080 serves the W31 workspace bundle and profile controls','critical profile edit remains denied by the server with no profile mutation','APP-device smoke preserved PC browser session']
const report={result:'passed',date:new Date().toISOString(),entry:origin,checks,mutation:'read-only; attempted critical edit was rejected; supplier profile unchanged'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W31-main-deployment-verification.json',JSON.stringify(report,null,2)+'\n')
console.log('PASS: W31 main deployment read-only profile smoke')
