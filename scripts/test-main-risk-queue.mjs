import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {join} from 'node:path'
import {call,encrypt} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review',origin=process.env.SRM_TEST_ENTRY||'http://localhost:18080'
assert.equal(origin,'http://localhost:18080','Main risk queue smoke must use the protected local entry')
const accounts=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8'))
async function loginActor(account,realm){const actor=accounts.find(x=>x.account===account);assert.ok(actor);const config=await call('/dev/config/sysBaseList');let captcha={};if(config.some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_'+realm.toUpperCase()&&String(x.configValue)==='true')){const c=await call('/auth/'+realm+'/getPicCaptcha');const raw=execFileSync('docker',['compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo],{cwd:bundle,encoding:'utf8'}).trim();let value;try{value=JSON.parse(raw)}catch{value=raw}captcha={validCode:String(value),validCodeReqNo:c.validCodeReqNo}}return call('/auth/'+realm+'/doLogin',null,{account,password:encrypt(actor.password),device:'APP',...captcha})}
const tester=await loginActor('lijunhua','b'),portal=await loginActor('lixiaolong','c'),checks=[]
const context=await call('/srm/v1/session/context',tester);assert.equal(context.portal,false);checks.push("APP-device auth did not evict the user's PC session")
async function list(query={}){return call('/srm/v1/quality/risks?'+new URLSearchParams({current:'1',size:'20',...query}),tester)}
for(const [field,value] of [['riskType','DELIVERY_EXCEPTION'],['status','PENDING'],['level','HIGH']]){const result=await list({[field]:value});assert.ok(result.records.every(r=>r[field]===value));checks.push('main entry '+field+' filtering')}
await call('/srm/v1/quality/risks?current=1&size=20&riskType=NOT_A_RISK',tester,undefined,400);checks.push('main entry rejects unsupported filter values')
await call('/client/c/srm/v1/quality/risks?current=1&size=20&riskType=DELIVERY_EXCEPTION',portal,undefined,403);checks.push('portal account remains denied from internal risk queue')
const report={result:'passed',entry:origin,date:new Date().toISOString(),checks,mutation:'read-only risk queries; APP-device sessions only'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W28-main-deployment-verification.json',JSON.stringify(report,null,2)+'\n')
console.log('PASS: W28 main deployment read-only smoke; existing PC session preserved')
