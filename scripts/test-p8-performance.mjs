// 10k-row read-only list benchmark with synthetic rows in the isolated P8 clone.
import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {join} from 'node:path'
import {performance} from 'node:perf_hooks'
import {login,sql} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE,origin=process.env.SRM_TEST_ENTRY
if(!bundle||origin!=='http://localhost:18081')throw new Error('Isolated P8 clone at http://localhost:18081 required')
const proof=JSON.parse(readFileSync(join(bundle,'..','recovery-result.json'),'utf8'))
assert.equal(proof.project,'srm-drill-p8-20261028');assert.equal(proof.result,'passed')
const mark=sql('SELECT PROJECT FROM SRM_P8_DRILL_MARKER WHERE ID=1;')
assert.equal(mark,proof.project)
const profile=JSON.parse(readFileSync(join(bundle,'runtime/account-profile.json'),'utf8'))
const existing=Number(sql("SELECT COUNT(*) FROM SRM_SUPPLIER WHERE NAME LIKE 'P8PERF-%';"))
assert.equal(existing,0,'Refuse to duplicate synthetic performance fixtures')
const digits='SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9'
sql(`INSERT INTO SRM_SUPPLIER(ID,SUPPLIER_CODE,NAME,CATEGORY,TIER,STATUS,ORG_ID,DELETE_FLAG,CREATE_TIME,CREATE_USER,ROW_VERSION)
SELECT CAST(2800000000000000000+n AS CHAR),CONCAT('P8PERF-',LPAD(n,5,'0')),CONCAT('P8PERF-',LPAD(n,5,'0')),'ELECTRONICS','TEMPORARY','POTENTIAL',${JSON.stringify(profile.orgId)},'NOT_DELETE',NOW(),'P8-PERF',0
FROM (SELECT a.d+10*b.d+100*c.d+1000*d.d+1 n FROM (${digits}) a CROSS JOIN (${digits}) b CROSS JOIN (${digits}) c CROSS JOIN (${digits}) d) seq;`)
assert.equal(Number(sql("SELECT COUNT(*) FROM SRM_SUPPLIER WHERE NAME LIKE 'P8PERF-%' AND ORG_ID='"+profile.orgId+"';")),10000)
const user=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8')).find(x=>x.account==='lijunhua')
const token=await login(user,'b')
const url=origin+'/api/srm/v1/suppliers?current=1&size=20'
async function sample(){const start=performance.now(),r=await fetch(url,{headers:{token,Connection:'close'}}),body=await r.json();assert.equal(body.code,200);assert.ok(body.data.records.length<=20);assert.ok(Number(body.data.total)>=10000);return performance.now()-start}
for(let i=0;i<15;i++)await sample()
const timings=[]
for(let base=0;base<100;base+=8)timings.push(...await Promise.all(Array.from({length:Math.min(8,100-base)},sample)))
const sorted=[...timings].sort((a,b)=>a-b),p95=sorted[Math.ceil(.95*sorted.length)-1],p50=sorted[Math.floor(.50*sorted.length)]
const report={result:p95<1000?'passed':'target-missed',entry:origin,project:proof.project,dataset:'10,000 retained, synthetic POTENTIAL suppliers in clone only; no active supplier identities created',measuredRequests:timings.length,warmupRequests:15,concurrency:8,p50Ms:Number(p50.toFixed(2)),p95Ms:Number(p95.toFixed(2)),maxMs:Number(Math.max(...timings).toFixed(2)),requirement:'P95 < 1000 ms',productionCapacityClaim:false}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/P8-performance-verification.json',JSON.stringify(report,null,2)+'\n')
console.log(`P8 10k-list P95 ${report.p95Ms} ms; ${report.result}`)
if(report.result!=='passed')process.exitCode=1
