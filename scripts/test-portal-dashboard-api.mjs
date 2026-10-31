import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {join} from 'node:path'
import {call,sql,encrypt} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE||'artifacts/SRM-local-review'
const origin=process.env.SRM_TEST_ENTRY||'http://localhost:18081'
assert.equal(origin,'http://localhost:18081','Portal dashboard regression must use the isolated review entry')
const recovery=JSON.parse(readFileSync(join(bundle,'..','recovery-result.json'),'utf8'))
assert.equal(recovery.project,'srm-drill-p8-20261028')
const users=JSON.parse(readFileSync(join(bundle,'runtime/accounts.json'),'utf8'))
const profile=JSON.parse(readFileSync(join(bundle,'runtime/account-profile.json'),'utf8'))
const actor=users.find(x=>x.account==='gysadmin')
assert.ok(actor,'portal administrator test account is available')
const config=await call('/dev/config/sysBaseList')
const captchaOpen=config.some(x=>x.configKey==='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_C'&&String(x.configValue)==='true')
let captcha={}
if(captchaOpen){
 const c=await call('/auth/c/getPicCaptcha')
 const args=['docker','compose','--project-directory',bundle,'--env-file',join(bundle,'runtime/compose.env'),'-f',join(bundle,'compose.drill.json'),'-p',recovery.project,'exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+c.validCodeReqNo]
 const raw=execFileSync(args[0],args.slice(1),{encoding:'utf8'}).trim();let value
 try{value=JSON.parse(raw)}catch{value=raw}
 captcha={validCode:String(value),validCodeReqNo:c.validCodeReqNo}
}
const token=await call('/auth/c/doLogin',null,{account:actor.account,password:encrypt(actor.password),device:'APP',...captcha})
const base='/client/c/srm/v1',context=await call(base+'/session/context',token)
assert.equal(context.portal,true)
assert.equal(context.supplierId,profile.supplierId)
const supplierId=profile.supplierId,checks=[],counts={}
assert.match(supplierId,/^\d+$/,'test supplier ID is a local numeric identifier')
const rfqs=await call(base+'/sourcing/rfqs?current=1&size=100',token)
const ownedRfqIds=new Set(sql(`SELECT DISTINCT r.ID FROM SRM_SRC_RFQ r JOIN SRM_SRC_INVITE i ON i.RFQ_ID=r.ID JOIN SRM_SRC_NOTICE n ON n.RFQ_ID=i.RFQ_ID AND n.SUPPLIER_ID=i.SUPPLIER_ID AND n.KIND='INVITATION' WHERE i.SUPPLIER_ID='${supplierId}' AND r.STATUS<>'DRAFT'`).split('\n').filter(Boolean))
assert.ok(rfqs.records.every(r=>ownedRfqIds.has(String(r.id))))
counts.rfqs=rfqs.records.length;checks.push('RFQ dashboard source contains only invitations bound to the logged-in supplier')
const sourceNotices=await call(base+'/sourcing/notices',token)
assert.ok(Array.isArray(sourceNotices));counts.sourceNotices=sourceNotices.length;checks.push('sourcing reminders load through the portal-scoped source API')
const recons=await call(base+'/collaboration/reconciliations?current=1&size=100',token)
for(const row of recons.records){const detail=await call(base+'/collaboration/reconciliations/'+row.id,token);assert.equal(detail.supplierId,supplierId)}
counts.reconciliations=recons.records.length;checks.push('reconciliation summary/detail stays in supplier scope')
const scars=await call(base+'/quality/cases?kind=SCAR&current=1&size=100',token)
for(const row of scars.records){const detail=await call(base+'/quality/cases/'+row.id,token);assert.equal(detail.supplierId,supplierId)}
counts.scars=scars.records.length;checks.push('SCAR summary/detail stays in supplier scope')
const scores=await call(base+'/performance/scores?current=1&size=100',token)
assert.ok(scores.records.every(r=>r.supplierId===supplierId))
counts.scores=scores.records.length;checks.push('performance overview contains only the bound supplier scores')
const alerts=await call(base+'/alerts',token)
assert.ok(alerts.every(r=>r.supplierId===supplierId))
counts.alerts=alerts.length;checks.push('qualification alerts contain only the bound supplier')
const qualityNotices=await call(base+'/quality/notices',token)
assert.ok(Array.isArray(qualityNotices));counts.qualityNotices=qualityNotices.length;checks.push('quality notices can be read without changing quality records')
const collaborationNotices=await call(base+'/collaboration/notices',token)
assert.ok(Array.isArray(collaborationNotices));counts.collaborationNotices=collaborationNotices.length;checks.push('collaboration notices can be read without changing reconciliation records')
const report={result:'passed',entry:origin,project:recovery.project,date:new Date().toISOString(),checks,counts,mutation:'GET-only business queries; APP-device auth session; no fixture changes'}
mkdirSync('docs/review',{recursive:true});writeFileSync('docs/review/W26-portal-dashboard-api-verification.json',JSON.stringify(report,null,2)+'\n')
console.log(`PASS: ${checks.length} portal dashboard API checks; counts only, no business rows changed`)
