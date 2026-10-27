import {existsSync,mkdirSync,writeFileSync} from 'node:fs'
import crypto from 'node:crypto'
mkdirSync('runtime',{recursive:true,mode:0o700})
if(existsSync('runtime/compose.env')){console.log('Existing local accounts, keys and database retained.');process.exit(0)}
const dbPassword=crypto.randomBytes(24).toString('hex')
const env='SRM_DB_PASSWORD='+dbPassword+'\nSRM_DATA_KEY_ID=review-v1\nSRM_STORAGE_ENGINE=LOCAL\n'+[['SRM_ENCRYPTION_KEY',16],['SRM_MAC_KEY',32],['SRM_INDEX_KEY',32]].map(([k,n])=>k+'='+crypto.randomBytes(n).toString('base64')).join('\n')+'\n'
let serial=0;const base=BigInt(Date.now())*1000000n,id=()=>String(base+BigInt(++serial)),org=id(),accounts=[]
const statements=[`INSERT INTO SYS_ORG(ID,PARENT_ID,NAME,CODE,CATEGORY,DELETE_FLAG,CREATE_TIME) VALUES('${org}','0','SRM本地验收组织','SRM-LOCAL-REVIEW','COMPANY','NOT_DELETE',NOW());`]
const roles={};for(const code of ['SUP-ADMIN','SQE','CAT-MGR','SYS-ADMIN']){roles[code]=id();statements.push(`INSERT INTO SYS_ROLE(ID,NAME,CODE,CATEGORY,DELETE_FLAG,CREATE_TIME) VALUES('${roles[code]}','${code}','${code}','GLOBAL','NOT_DELETE',NOW());`)}
for(const [account,name,codes] of [['srm.owner','发起人',['SUP-ADMIN','CAT-MGR']],['srm.sqe','SQE审核员',['SQE']],['srm.manager','采购经理审批员',['CAT-MGR']],['srm.flowadmin','流程管理员',['SYS-ADMIN']]]){
 const uid=id(),password=crypto.randomBytes(9).toString('base64url')+'A9!',hash=crypto.createHash('sm3').update(password).digest('hex')
 statements.push(`INSERT INTO SYS_USER(ID,ACCOUNT,PASSWORD,NAME,USER_STATUS,ORG_ID,DELETE_FLAG,CREATE_TIME) VALUES('${uid}','${account}','${hash}','${name}','ENABLE','${org}','NOT_DELETE',NOW());`)
 for(const code of codes)statements.push(`INSERT INTO SYS_RELATION(ID,OBJECT_ID,TARGET_ID,CATEGORY) VALUES('${id()}','${uid}','${roles[code]}','SYS_USER_HAS_ROLE');`)
 for(const api of ['/srm/v1/**','/srm/fields/sensitive'])statements.push(`INSERT INTO SYS_RELATION(ID,OBJECT_ID,TARGET_ID,CATEGORY,EXT_JSON) VALUES('${id()}','${uid}','${api}','SYS_USER_HAS_PERMISSION','{"scopeCategory":"SCOPE_ORG"}');`)
 accounts.push({account,password,name,orgId:org})
}
writeFileSync('runtime/backend.env',env,{mode:0o600});writeFileSync('runtime/99-accounts.sql','START TRANSACTION;\n'+statements.join('\n').replaceAll('INSERT INTO','INSERT IGNORE INTO')+'\nCOMMIT;\n',{mode:0o600});writeFileSync('runtime/accounts.json',JSON.stringify(accounts,null,2),{mode:0o600});writeFileSync('runtime/登录账号.txt',accounts.map(a=>a.name+'：'+a.account+'\n密码：'+a.password).join('\n\n')+'\n\n仅用于本机验收，不要用于生产或转发。\n',{mode:0o600})
writeFileSync('runtime/compose.env','SRM_DB_PASSWORD='+dbPassword+'\n',{mode:0o600});console.log('New local-only accounts prepared in runtime/登录账号.txt. Passwords were not printed.')
