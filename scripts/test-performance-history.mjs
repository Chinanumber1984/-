import{readFileSync,writeFileSync}from'node:fs'
import crypto from'node:crypto'
import{execFileSync}from'node:child_process'
import assert from'node:assert/strict'
import{call,login}from'./test-performance-api.mjs'
const f=JSON.parse(readFileSync('.runtime/performance/fixture.json')),a=await login(f.owner),pt2=await login(f.otherPortal,'c'),pt=await login(f.portal,'c'),base='/srm/v1/performance'
const rows=Array.from({length:12},(_,i)=>{const year=i<3?2025:2026,month=i<3?i+10:i-2;return{supplierId:f.secondSupplier,period:`${year}-${String(month).padStart(2,'0')}`,sourceType:'SANDBOX',sourceRef:'TWELVE-MONTH-TEST-'+i,scores:[75,75,75,75]}})
await call(base+'/imports',a,{requestKey:crypto.randomUUID(),rows})
await Promise.all(rows.map(row=>call(base+'/calculate',a,{supplierId:row.supplierId,period:row.period})))
const summary=await call(base+'/suppliers/'+f.secondSupplier+'/summary?endPeriod=2026-09',a)
assert.equal(summary.validMonths,12);assert.equal(summary.insufficientHistory,false);assert.equal(summary.requiresApprovedException,false);assert.equal(summary.missingPeriods.length,0);assert.equal(Number(summary.averageTotal),75);assert.equal(summary.trend.length,12)
const own=await call('/client/c/srm/v1/performance/scores',pt2);assert.equal(own.total,12);assert.ok(own.records.every(x=>x.supplierId===f.secondSupplier));await call('/client/c/srm/v1/performance/scores/'+own.records[0].id,pt,undefined,404)
const exportResponse=await fetch((process.env.SRM_TEST_ENTRY||'http://localhost:18080')+'/api/client/c/srm/v1/performance/export',{headers:{token:pt2}})
writeFileSync('.runtime/performance/portal-isolated-export.xlsx',new Uint8Array(await exportResponse.arrayBuffer()))
const xml=execFileSync('python3',['-c','import zipfile;print(zipfile.ZipFile(".runtime/performance/portal-isolated-export.xlsx").read("xl/sharedStrings.xml").decode())'],{encoding:'utf8'})
assert.ok(xml.includes('同组织隔离'));assert.ok(!xml.includes('绩效验收'))
writeFileSync('docs/performance/history-verification.json',JSON.stringify({result:'passed',entry:'http://localhost:18080/',checks:['actual_twelve_month_history_complete_and_exact_mean','parallel_period_calculation_serializes_per_supplier','two_suppliers_with_real_scores_remain_isolated_in_portal','xlsx_export_with_populated_suppliers_preserves_supplier_isolation']},null,2)+'\n');console.log('PASS: 4 complete-history and supplier-isolation checks.')
