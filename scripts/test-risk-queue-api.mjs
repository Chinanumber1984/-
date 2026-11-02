import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {login,call} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review'
const users=JSON.parse(readFileSync(bundle+'/runtime/accounts.json','utf8'))
const tester=await login(users.find(x=>x.account==='lijunhua'))
const portal=await login(users.find(x=>x.account==='lixiaolong'),'c')
const checks=[],pass=s=>{checks.push(s);console.log('PASS:',s)}
const base='/srm/v1/quality/risks'
async function list(query={},expected=200){return call(base+'?'+new URLSearchParams({current:'1',size:'100',...query}),tester,undefined,expected)}

const all=await list();assert.ok(all.records.length<=100);assert.ok(all.records.every(r=>r.id&&r.supplierName&&r.originKind));pass('paged internal risk list includes origin classification')
for(const [field,value] of [['riskType','DELIVERY_EXCEPTION'],['status','PENDING'],['status','ASSESSED'],['status','WARNING'],['status','DISPOSED'],['level','HIGH'],['level','MEDIUM'],['level','LOW']]){
 const result=await list({[field]:value});assert.ok(result.records.every(r=>r[field]===value),`${field} filter leaked a nonmatching row`)
}
pass('risk type, lifecycle status and severity filters return only matching rows')
const compound=await list({riskType:'DELIVERY_EXCEPTION',status:'PENDING',level:'HIGH'});assert.ok(compound.records.every(r=>r.riskType==='DELIVERY_EXCEPTION'&&r.status==='PENDING'&&r.level==='HIGH'));pass('compound risk filters are combined with AND')
await list({riskType:'NOT_A_RISK'},400);await list({status:'IN_PROGRESS'},400);await list({level:'CRITICAL'},400);pass('unsupported filter values are rejected')
await call('/client/c/srm/v1/quality/risks?current=1&size=20&riskType=DELIVERY_EXCEPTION',portal,undefined,403);pass('supplier portal cannot access internal risk queue')
const report={result:'passed',entry:process.env.SRM_TEST_ENTRY||'http://localhost:18080',date:new Date().toISOString(),checks,mutation:'read-only; no business rows changed'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W28-risk-queue-api-verification.json',JSON.stringify(report,null,2)+'\n')
