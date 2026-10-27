import {readFileSync} from 'node:fs'
import {createRequire} from 'node:module'
const require=createRequire(import.meta.url),{sm2}=require('./vendor/node_modules/sm-crypto')
const publicKey=readFileSync('public-key.txt','utf8').trim(),account=JSON.parse(readFileSync('runtime/accounts.json','utf8')).find(a=>a.account==='srm.flowadmin')
async function api(path,body,token){const r=await fetch('http://backend:8080'+path,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{token}:{})},...(body?{body:JSON.stringify(body)}:{})});const b=await r.json();if(b.code!==200)throw new Error('Review initialization failed at '+path+'; business code '+b.code);return b.data}
const token=await api('/auth/b/doLogin',{account:account.account,password:sm2.doEncrypt(account.password,publicKey,1),device:'PC'})
try{const existing=await api('/srm/v1/workflow/definitions',null,token);for(const kind of ['ONBOARD','CHANGE','FREEZE','UNFREEZE','ELIMINATE'])if(!existing.some(d=>d.key==='srm_'+kind.toLowerCase()))await api('/srm/v1/workflow/definitions',{kind},token);console.log('Review workflows ready; existing versions retained.')}finally{await api('/auth/b/doLogout',null,token)}
