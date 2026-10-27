// Local development bootstrap only; never reset an existing account or print credential values.
import {existsSync,readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import crypto from 'node:crypto'
import {createRequire} from 'node:module'
import {sql} from './test-workspace-api.mjs'
const require=createRequire(new URL('../snowy-admin-web/package.json',import.meta.url));const{sm3}=require('sm-crypto')
mkdirSync('.runtime',{recursive:true})
const path='.runtime/workspace-access.json'
if(existsSync(path)){console.log('Existing private local workspace access retained.');process.exit(0)}
const account='srm.owner'
if(sql("SELECT COUNT(*) FROM SYS_USER WHERE ACCOUNT='srm.owner';")!=='0')throw new Error('Existing srm.owner account retained; no password reset performed')
const org=sql("SELECT ID FROM SYS_ORG WHERE DELETE_FLAG='NOT_DELETE' AND COALESCE(CODE,'') NOT LIKE 'W36%' ORDER BY CREATE_TIME,ID LIMIT 1;")
if(!/^[0-9]{1,20}$/.test(org))throw new Error('No valid development organization found')
const uid=String(BigInt(Date.now())*1000000n+BigInt(crypto.randomInt(1,999999)))
let serial=0;const id=()=>String(BigInt(Date.now())*1000000n+BigInt(++serial))
const password=crypto.randomBytes(9).toString('base64url')+'A9!'
sql(`INSERT INTO SYS_USER(ID,ACCOUNT,PASSWORD,NAME,USER_STATUS,ORG_ID,DELETE_FLAG,CREATE_TIME) VALUES('${uid}','${account}','${sm3(password)}','SRM本地开发管理员','ENABLE','${org}','NOT_DELETE',NOW());`)
for(const code of ['SUP-ADMIN','CAT-MGR']){
 let rid=sql(`SELECT ID FROM SYS_ROLE WHERE CODE='${code}' AND DELETE_FLAG='NOT_DELETE' LIMIT 1;`)
 if(!rid){rid=id();sql(`INSERT INTO SYS_ROLE(ID,NAME,CODE,CATEGORY,DELETE_FLAG,CREATE_TIME) VALUES('${rid}','${code}','${code}','GLOBAL','NOT_DELETE',NOW());`)}
 sql(`INSERT INTO SYS_RELATION(ID,OBJECT_ID,TARGET_ID,CATEGORY) VALUES('${id()}','${uid}','${rid}','SYS_USER_HAS_ROLE');`)
}
for(const target of ['/srm/v1/**','/srm/fields/sensitive'])sql(`INSERT INTO SYS_RELATION(ID,OBJECT_ID,TARGET_ID,CATEGORY,EXT_JSON) VALUES('${id()}','${uid}','${target}','SYS_USER_HAS_PERMISSION','{"scopeCategory":"SCOPE_ORG"}');`)
writeFileSync(path,JSON.stringify({account,password,orgId:org,userId:uid},null,2),{mode:0o600})
writeFileSync('.runtime/workspace-access.md',`# 本机开发登录信息\n\n仅用于本地 srm-local 开发环境；不要提交、转发或用于生产。\n\n- 登录入口：供应商真实工作台，选择“内部工作台”\n- 账号：${account}\n- 初始密码：${password}\n- 组织：${org}\n- 角色：供应商主数据管理员、采购经理\n- 字段权限：本组织信用代码、银行账号可见\n\n首次使用后请在正式账号管理中按实际人员管理密码与角色。该文件权限为600，已被Git忽略。\n`,{mode:0o600})
console.log('Local workspace access prepared in private ignored files; no credential values printed.')
