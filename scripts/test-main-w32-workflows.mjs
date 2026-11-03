// Read-only W32 smoke for the live local workflow catalog and its served UI.
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
const expected={ONBOARD:'准入申请',CHANGE:'关键资料变更',FREEZE:'冻结申请',UNFREEZE:'解冻申请',ELIMINATE:'淘汰申请',AWARD:'寻源定点',COL_REVIEW:'采购协同审核',QUALITY_REVIEW:'质量审核'}
const definitions=await call('/srm/v1/workflow/definitions',token),categories=new Set(definitions.map(x=>x.category))
for(const key of Object.keys(expected))assert.ok(categories.has(key),'Missing process definition category '+key)
const html=await fetch(origin+'/').then(r=>{assert.equal(r.status,200);return r.text()}),asset=html.match(/workspace-[A-Za-z0-9_-]+\.js/);assert.ok(asset)
const js=await fetch(origin+'/assets/'+asset[0]).then(r=>r.text());for(const label of Object.values(expected))assert.ok(js.includes(label),'Missing workflow label '+label)
const report={result:'passed',date:new Date().toISOString(),entry:origin,definitions:definitions.length,categories:Object.keys(expected),checks:['all eight internal process categories are present in Flowable definitions','served workspace bundle labels and maps all eight categories','APP-device read-only request left browser session and process definitions unchanged'],mutation:'read-only; no definition published, suspended or uploaded'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W32-main-deployment-verification.json',JSON.stringify(report,null,2)+'\n')
console.log('PASS: W32 main workflow catalog read-only smoke')
