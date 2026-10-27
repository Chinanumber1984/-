import {readFileSync,writeFileSync} from 'node:fs'
import crypto from 'node:crypto'
import {call,login,credit} from './test-workspace-api.mjs'
const f=JSON.parse(readFileSync('.runtime/w36-fixture.json','utf8')),t=await login(f.a)
let s=await call('/srm/v1/suppliers',t,{name:'W79浏览器准入-'+crypto.randomBytes(4).toString('hex'),category:'MACHINING',creditCode:credit('9111'+String(Date.now()).slice(-10)+String(crypto.randomInt(100,999))),bankAccount:'6222000012345678',address:'W79真实浏览器测试园区'})
await call('/srm/v1/suppliers/'+s.id+'/contacts',t,{name:'W79浏览器管理员',phone:'13800138000',roles:['ADMIN'],enabled:true,rowVersion:0})
const data=new FormData();data.append('file',new Blob(['%PDF-1.4\n%%EOF'],{type:'application/pdf'}),'ui-review.pdf');const file=await call('/srm/v1/suppliers/'+s.id+'/attachments',t,data)
for(const type of ['BUSINESS_LICENSE','ISO9001'])await call('/srm/v1/suppliers/'+s.id+'/certificates',t,{certType:type,certNo:'UI-'+type,validFrom:'2026-01-01',validTo:'2030-01-01',fileId:file.fileId})
writeFileSync('.runtime/lifecycle-browser-fixture.json',JSON.stringify({supplierId:s.id,name:s.name},null,2),{mode:0o600});console.log('Prepared a real supplier for lifecycle browser acceptance.')
