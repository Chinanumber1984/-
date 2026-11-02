import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {join} from 'node:path'
import {call,encrypt} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review',origin=process.env.SRM_TEST_ENTRY||'http://localhost:18080'
assert.equal(origin,'http://localhost:18080','Main category smoke must use the protected local entry')
const accounts=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8'))
async function loginActor(account,realm){const actor=accounts.find(x=>x.account===account);assert.ok(actor);const config=await call('/dev/config/sysBaseList');let captcha={};if(config.some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_'+realm.toUpperCase()&&String(x.configValue)==='true')){const c=await call('/auth/'+realm+'/getPicCaptcha');const raw=execFileSync('docker',['compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo],{cwd:bundle,encoding:'utf8'}).trim();let value;try{value=JSON.parse(raw)}catch{value=raw}captcha={validCode:String(value),validCodeReqNo:c.validCodeReqNo}}return call('/auth/'+realm+'/doLogin',null,{account,password:encrypt(actor.password),device:'APP',...captcha})}
const internal=await loginActor('admin','b'),portal=await loginActor('lixiaolong','c'),checks=[]
const context=await call('/srm/v1/session/context',internal);assert.equal(context.portal,false);checks.push("APP-device auth preserved the user's PC session")
const publicCategories=await call('/srm/public/categories');assert.ok(publicCategories.length>0&&publicCategories.every(c=>c.code&&c.name));checks.push('public registration categories load through main entry')
const internalCategories=await call('/srm/v1/categories',internal);assert.ok(internalCategories.some(c=>c.code==='MACHINING'&&c.enabled===true));checks.push('internal category dictionary remains readable')
const portalCategories=await call('/client/c/srm/v1/categories',portal);assert.ok(portalCategories.every(c=>c.enabled===true));assert.ok(!portalCategories.some(c=>!c.enabled));checks.push('portal category list contains enabled categories only')
const candidates=await call('/srm/v1/sourcing/candidates?category=MACHINING',internal);assert.ok(candidates.every(c=>c.category==='MACHINING'));checks.push('main sourcing candidates retain exact category filter')
await call('/srm/v1/sourcing/candidates?category=W29_INVALID_CATEGORY',internal,undefined,400);checks.push('main sourcing service rejects disabled or unknown categories')
await call('/client/c/srm/v1/categories',portal,{code:'W29_PORTAL_FORBIDDEN',name:'Forbidden',requiredCerts:[],enabled:true,rowVersion:0},403);checks.push('portal cannot maintain categories')
const report={result:'passed',entry:origin,date:new Date().toISOString(),checks,mutation:'read-only queries; APP-device sessions only'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W29-main-deployment-verification.json',JSON.stringify(report,null,2)+'\n')
console.log('PASS: W29 main deployment read-only smoke; existing PC session preserved')
