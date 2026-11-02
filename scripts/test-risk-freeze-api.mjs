// Targeted W30 API verification on an isolated review clone.
import assert from 'node:assert/strict'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {randomUUID} from 'node:crypto'
import {call,login,sql,credit} from './test-performance-api.mjs'

const bundle=process.env.SRM_TEST_BUNDLE
if(!bundle||process.env.SRM_TEST_ENTRY!=='http://localhost:18081')throw new Error('W30 checks require the isolated clone on localhost:18081')
const runtime='artifacts/SRM-local-review/runtime/'
const users=JSON.parse(readFileSync(runtime+'accounts.json','utf8'))
const profile=JSON.parse(readFileSync(runtime+'account-profile.json','utf8'))
const tester=await login(users.find(u=>u.account==='lijunhua'))
const manager=await login(users.find(u=>u.account==='admin'))
const portal=await login(users.find(u=>u.account==='lixiaolong'),'c')
const key=()=>randomUUID(),base='/srm/v1',quality=base+'/quality',checks=[]
const pass=name=>{checks.push(name);console.log('PASS:',name)}
async function upload(path,token){const form=new FormData();form.append('file',new Blob(['%PDF-1.4\nW30 isolated evidence\n%%EOF'],{type:'application/pdf'}),'W30-evidence.pdf');return call(path,token,form)}

const current=await call(base+'/suppliers/'+profile.supplierId,tester)
const supplier=await call(base+'/suppliers',tester,{name:'W30隔离自动冻结验收-'+Date.now(),category:current.category,creditCode:credit()})
await call(base+'/suppliers/'+supplier.id+'/contacts',tester,{name:'W30隔离联系人',phone:'13800138000',roles:['ADMIN'],enabled:true,rowVersion:0})
const file=await upload(base+'/suppliers/'+supplier.id+'/attachments',tester)
let detail=await call(base+'/suppliers/'+supplier.id,tester)
for(const type of detail.qualification.required)await call(base+'/suppliers/'+supplier.id+'/certificates',tester,{certType:type,certNo:'W30-'+type,validFrom:'2026-01-01',validTo:'2030-01-01',fileId:file.fileId})
detail=await call(base+'/suppliers/'+supplier.id,tester)
let approval=await call(base+'/suppliers/'+supplier.id+'/approvals',tester,{requestKey:key(),kind:'ONBOARD',rowVersion:detail.rowVersion,reason:'W30隔离自动冻结样例准入'})
for(let i=0;i<2;i++)approval=await call(base+'/approvals/'+approval.id+'/tasks/'+approval.tasks[0].id,manager,{action:'APPROVE',comment:'W30隔离样例准入核验'})
assert.equal((await call(base+'/suppliers/'+supplier.id,tester)).status,'QUALIFIED')

let medium=await call(quality+'/risks',tester,{requestKey:key(),supplierId:supplier.id,description:'W30中风险隔离用例',evidenceRef:'W30-medium-evidence'})
medium=await call(quality+'/risks/'+medium.id+'/assess',tester,{rowVersion:medium.rowVersion,level:'MEDIUM',comment:'W30中风险不触发冻结建议'})
assert.equal(medium.freezeSuggested,false);assert.equal(medium.freezeApprovalId,null)
pass('non_high_assessment_does_not_submit_freeze')

let risk=await call(quality+'/risks',tester,{requestKey:key(),supplierId:supplier.id,description:'W30高风险隔离用例',evidenceRef:'W30-high-evidence'})
risk=await call(quality+'/risks/'+risk.id+'/assess',tester,{rowVersion:risk.rowVersion,level:'HIGH',comment:'W30高风险由原审批流程复核'})
assert.equal(risk.freezeSuggested,true);assert.ok(risk.freezeApprovalId)
assert.equal((await call(base+'/suppliers/'+supplier.id,tester)).status,'QUALIFIED')
approval=await call(base+'/approvals/'+risk.freezeApprovalId,tester)
assert.equal(approval.kind,'FREEZE');assert.equal(approval.status,'PENDING')
await call(base+'/approvals/'+approval.id+'/tasks/'+approval.tasks[0].id,tester,{action:'APPROVE',comment:'W30禁止本人审批'},403)
pass('high_assessment_automatically_submits_pending_original_flow_and_requester_cannot_self_approve')

const replay=await call(quality+'/risks/'+risk.id+'/assess',tester,{rowVersion:risk.rowVersion,level:'HIGH',comment:'W30重复评估检查'})
assert.equal(replay.freezeApprovalId,risk.freezeApprovalId)
assert.equal(sql(`SELECT COUNT(*) FROM SRM_APPROVAL_LINK WHERE ID='${risk.freezeApprovalId}' AND BUSINESS_TYPE='FREEZE' AND SUPPLIER_ID='${supplier.id}'`),'1')
assert.equal((await call(base+'/suppliers/'+supplier.id,tester)).status,'QUALIFIED')
pass('repeat_high_assessment_keeps_same_approval_without_freezing_supplier')

await call(base+'/approvals/'+approval.id+'/tasks/'+approval.tasks[0].id,manager,{action:'APPROVE',comment:'W30隔离样例经理批准冻结'})
assert.equal((await call(base+'/suppliers/'+supplier.id,tester)).status,'FROZEN')
approval=await call(base+'/suppliers/'+supplier.id+'/approvals',tester,{requestKey:key(),kind:'UNFREEZE',rowVersion:(await call(base+'/suppliers/'+supplier.id,tester)).rowVersion,reason:'W30验证完成，恢复隔离样例'})
await call(base+'/approvals/'+approval.id+'/tasks/'+approval.tasks[0].id,manager,{action:'APPROVE',comment:'W30隔离样例经理批准解冻'})
assert.equal((await call(base+'/suppliers/'+supplier.id,tester)).status,'QUALIFIED')
pass('manager_approval_freezes_supplier_and_existing_unfreeze_flow_restores_qualified')

await call('/client/c/srm/v1/quality/risks',portal,undefined,403)
pass('supplier_portal_cannot_access_internal_risk_queue')

mkdirSync('docs/review',{recursive:true})
writeFileSync('docs/review/W30-risk-freeze-api-verification.json',JSON.stringify({result:'passed',date:new Date().toISOString(),entry:process.env.SRM_TEST_ENTRY,checks,supplierId:supplier.id,riskId:risk.id,approvalId:risk.freezeApprovalId,accountAliases:['lijunhua','admin','lixiaolong'],scope:'isolated recovery clone; supplier restored to QUALIFIED; primary data untouched'},null,2)+'\n')
console.log(`PASS: ${checks.length} W30 risk-freeze API checks`)
