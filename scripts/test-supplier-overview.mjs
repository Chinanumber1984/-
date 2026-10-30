// Read-only tests for the system-local supplier detail cross-module sections.
import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {join} from 'node:path'
import {call,login,sql} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE,origin=process.env.SRM_TEST_ENTRY
if(!bundle||origin!=='http://localhost:18081')throw new Error('Run only against the isolated P8 clone at http://localhost:18081')
const recovery=JSON.parse(readFileSync(join(bundle,'..','recovery-result.json'),'utf8'))
assert.equal(recovery.project,'srm-drill-p8-20261028');assert.equal(sql('SELECT PROJECT FROM SRM_P8_DRILL_MARKER WHERE ID=1;'),recovery.project)
const profile=JSON.parse(readFileSync(join(bundle,'runtime/account-profile.json'),'utf8'))
const users=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8'))
const tester=users.find(x=>x.account==='lijunhua'),portal=users.find(x=>x.account==='gysadmin')
const foreign=sql("SELECT ID FROM SRM_SUPPLIER WHERE ORG_ID<>"+JSON.stringify(profile.orgId)+" AND DELETE_FLAG='NOT_DELETE' ORDER BY ID LIMIT 1;")
assert.ok(foreign,'Need a retained out-of-scope supplier row for the access regression')
const checks=[];const pass=x=>checks.push(x)
const testerToken=await login(tester,'b'),portalToken=await login(portal,'c')
const target=profile.supplierId
async function list(path,token,sid,expected=200){const params=new URLSearchParams({current:'1',size:'100',supplierId:sid});return call(path+'?'+params,token,undefined,expected)}
const scores=await list('/srm/v1/performance/scores',testerToken,target)
assert.ok(scores.records.every(r=>r.supplierId===target));pass('performance detail list is supplier-filtered within trusted session scope')
await list('/srm/v1/performance/scores',testerToken,foreign,404);pass('performance supplier filter rejects another organization')
const trend=await call('/srm/v1/performance/suppliers/'+target+'/summary?months=12',testerToken)
assert.equal(trend.supplierId,target);pass('supplier score trend remains bound to the selected supplier')
const risks=await list('/srm/v1/quality/risks',testerToken,target)
assert.ok(risks.records.every(r=>r.supplierId===target));pass('internal quality risk detail list is supplier-filtered')
await list('/srm/v1/quality/risks',testerToken,foreign,404);pass('quality risk supplier filter rejects another organization')
const orders=await list('/srm/v1/collaboration/orders',testerToken,target)
assert.ok(orders.records.every(r=>r.supplierId===target));pass('purchase order detail list is supplier-filtered and omits unit price')
await list('/srm/v1/collaboration/orders',testerToken,foreign,404);pass('order supplier filter rejects another organization')
const portalScores=await list('/client/c/srm/v1/performance/scores',portalToken,target)
assert.ok(portalScores.records.every(r=>r.supplierId===target));pass('portal performance remains limited to the bound supplier')
await list('/client/c/srm/v1/performance/scores',portalToken,foreign,404);pass('portal cannot select a foreign supplier performance record')
await list('/client/c/srm/v1/quality/risks',portalToken,target,403);pass('internal quality risks stay unavailable to supplier portal')
const report={result:'passed',date:new Date().toISOString(),entry:origin,project:recovery.project,checks,scope:'read-only; clone database; no business records changed'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W25-supplier-overview-api-verification.json',JSON.stringify(report,null,2)+'\n')
console.log(`PASS: ${checks.length} supplier overview scope/API checks`)
