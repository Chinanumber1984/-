// Verify that sourcing completion events do not break the original two-level workflow.
import {call,login,credit} from './test-performance-api.mjs'
import {readFileSync,writeFileSync} from 'node:fs'
import crypto from 'node:crypto'
import assert from 'node:assert/strict'
const f=JSON.parse(readFileSync('.runtime/sourcing/fixture.json')),a=await login(f.owner),sqe=await login(f.sqe),manager=await login(f.manager),checks=[]
const supplier=await call('/srm/v1/suppliers',a,{name:'W12–15原两级审批回归-'+Date.now(),category:'MACHINING',creditCode:credit()})
await call('/srm/v1/suppliers/'+supplier.id+'/contacts',a,{name:'准入回归管理员',phone:'13800138000',roles:['ADMIN'],enabled:true,rowVersion:0})
const form=new FormData();form.append('file',new Blob(['%PDF-1.4\nonboarding regression\n%%EOF'],{type:'application/pdf'}),'onboarding-regression.pdf');const file=await call('/srm/v1/suppliers/'+supplier.id+'/attachments',a,form)
for(const certType of ['BUSINESS_LICENSE','ISO9001'])await call('/srm/v1/suppliers/'+supplier.id+'/certificates',a,{certType,certNo:'W12-REGRESSION-'+certType,validFrom:'2026-01-01',validTo:'2030-01-01',fileId:file.fileId})
let detail=await call('/srm/v1/suppliers/'+supplier.id,a);assert.equal(detail.qualification.complete,true);checks.push('fresh_credential_login_private_qualification_upload_and_validation')
let approval=await call('/srm/v1/suppliers/'+supplier.id+'/approvals',a,{requestKey:crypto.randomUUID(),kind:'ONBOARD',rowVersion:detail.rowVersion,reason:'W12–15交付后的原两级准入回归'})
approval=await call('/srm/v1/approvals/'+approval.id+'/tasks/'+approval.tasks[0].id,sqe,{action:'APPROVE',comment:'SQE已检查上传资质'});assert.equal((await call('/srm/v1/suppliers/'+supplier.id,a)).status,'REVIEWING');checks.push('sqe_first_approval_keeps_supplier_under_review')
approval=await call('/srm/v1/approvals/'+approval.id+'/tasks/'+approval.tasks[0].id,manager,{action:'APPROVE',comment:'经理批准准入回归'});detail=await call('/srm/v1/suppliers/'+supplier.id,a);assert.equal(detail.status,'QUALIFIED');assert.ok(detail.events.every(e=>e.signatureValid));checks.push('manager_second_approval_qualifies_supplier_with_signed_audit')
writeFileSync('docs/sourcing/onboarding-regression.json',JSON.stringify({result:'passed',date:new Date().toISOString(),entry:'http://localhost:18080/',checks,retainedIsolatedTestSupplier:true},null,2)+'\n');console.log('PASS: final login, private qualification upload and real SQE / manager two-level onboarding')
