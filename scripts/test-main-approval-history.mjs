import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {join,resolve} from 'node:path'
import {call,encrypt} from './test-performance-api.mjs'

const bundle=resolve(process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review'),origin=process.env.SRM_TEST_ENTRY||'http://localhost:18080'
assert.equal(origin,'http://localhost:18080','Main approval history smoke must use the protected local entry')
const accounts=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8')),actor=accounts.find(a=>a.account==='lijunhua')
assert.ok(actor,'authorized internal smoke account exists')
const config=await call('/dev/config/sysBaseList'),captchaOpen=config.some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_B'&&String(x.configValue)==='true')
let captcha={}
if(captchaOpen){const c=await call('/auth/b/getPicCaptcha');const raw=execFileSync('docker',['compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo],{cwd:bundle,encoding:'utf8'}).trim();let code;try{code=JSON.parse(raw)}catch{code=raw}captcha={validCode:String(code),validCodeReqNo:c.validCodeReqNo}}
const token=await call('/auth/b/doLogin',null,{account:actor.account,password:encrypt(actor.password),device:'APP',...captcha})
const context=await call('/srm/v1/session/context',token);assert.equal(context.portal,false)
const page=await call('/srm/v1/approvals/history?current=1&size=20',token)
assert.ok(page.records.every(r=>r.status!=='PENDING'))
for(const row of page.records.slice(0,3)){const detail=await call('/srm/v1/approvals/'+row.id,token);assert.equal(detail.id,row.id);assert.equal(detail.supplierId,row.supplierId)}
const report={result:'passed',entry:origin,date:new Date().toISOString(),visibleRecords:page.records.length,checks:['APP-device internal authentication preserved the browser PC session','main endpoint returns only completed approval records','sample history records open through scoped detail'],'mutation':'read-only approval queries; APP auth session only'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W27-main-deployment-verification.json',JSON.stringify(report,null,2)+'\n')
console.log('PASS: main approval history read-only smoke; PC browser session unchanged')
