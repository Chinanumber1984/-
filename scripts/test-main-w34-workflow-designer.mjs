// Read-only W34 smoke for the live workflow editor, Flowable catalog, and BPMN export.
import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {join} from 'node:path'
import {call,encrypt} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review',origin=process.env.SRM_TEST_ENTRY||'http://localhost:18080'
assert.equal(origin,'http://localhost:18080')
const accounts=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8')),actor=accounts.find(x=>x.account==='admin');assert.ok(actor)
const config=await call('/dev/config/sysBaseList');let captcha={}
if(config.some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_B'&&String(x.configValue)==='true')){
 const c=await call('/auth/b/getPicCaptcha'),raw=execFileSync('docker',['compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo],{cwd:bundle,encoding:'utf8'}).trim()
 let value;try{value=JSON.parse(raw)}catch{value=raw};captcha={validCode:String(value),validCodeReqNo:c.validCodeReqNo}
}
const token=await call('/auth/b/doLogin',null,{account:actor.account,password:encrypt(actor.password),device:'APP',...captcha})
const before=await call('/srm/v1/workflow/definitions',token),definition=before.find(x=>x.category==='ONBOARD');assert.ok(definition)
const xmlResponse=await fetch(origin+'/api/srm/v1/workflow/definitions/'+definition.id+'/xml',{headers:{token,Connection:'close'}})
assert.equal(xmlResponse.status,200);assert.match(await xmlResponse.text(),/<(?:\w+:)?process\b/)
const html=await fetch(origin+'/').then(r=>{assert.equal(r.status,200);return r.text()}),asset=html.match(/workspace-[A-Za-z0-9_-]+\.js/);assert.ok(asset)
const js=await fetch(origin+'/assets/'+asset[0]).then(r=>{assert.equal(r.status,200);return r.text()})
for(const label of ['图形设计','校验并发布新版本','流程图设计'])assert.ok(js.includes(label),'Missing BPMN designer UI: '+label)
assert.ok(js.includes('bpmn-js'),'Missing graphical BPMN modeler module')
const after=await call('/srm/v1/workflow/definitions',token)
assert.deepEqual(after.map(x=>[x.id,x.version,x.suspended]),before.map(x=>[x.id,x.version,x.suspended]))
const report={result:'passed',date:new Date().toISOString(),entry:origin,definitionCount:before.length,checks:['admin APP session reads existing definition and BPMN XML','18080 serves bundled graphical BPMN editor and publish controls','workflow definitions and versions unchanged by smoke'],mutation:'read-only; no process uploaded, published, suspended or activated'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W34-verification.json',JSON.stringify(report,null,2)+'\n')
console.log('PASS: W34 main workflow designer read-only smoke')
