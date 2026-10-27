// Single-origin review server: built assets only; the Snowy backend remains private.
import http from 'node:http'
import {createReadStream} from 'node:fs'
import {access,realpath,stat} from 'node:fs/promises'
import {resolve,extname,sep} from 'node:path'
import {Transform} from 'node:stream'
const root=await realpath(resolve('workspace/build'))
await access(resolve(root,'workspace.html'))
const host=process.env.SRM_REVIEW_HOST||'127.0.0.1',port=Number(process.env.SRM_REVIEW_PORT||5176)
if(!Number.isInteger(port)||port<1||port>65535)throw new Error('Invalid review port')
const local=new Set(['localhost','127.0.0.1','[::1]'])
const allowed=new Set((process.env.SRM_REVIEW_ALLOWED_HOSTS||'').split(',').map(s=>s.trim().toLowerCase()).filter(Boolean))
if(!['127.0.0.1','localhost','::1'].includes(host)&&allowed.size===0)throw new Error('External binding requires SRM_REVIEW_ALLOWED_HOSTS with the actual review hostname')
for(const h of allowed)if(h==='*'||!/^([a-z0-9][a-z0-9.-]*|\[::1\])$/.test(h))throw new Error('Allowed hosts must be exact hostnames or IPv4 addresses')
const backend=new URL(process.env.SRM_REVIEW_BACKEND||'http://127.0.0.1:8080')
if(backend.protocol!=='http:'||!new Set([...local,'backend']).has(backend.hostname)||backend.username||backend.password||backend.pathname!=='/'||backend.search||backend.hash)throw new Error('Review backend must be a private loopback or compose backend HTTP origin')
const hop=new Set(['host','connection','transfer-encoding','upgrade','proxy-authenticate','proxy-authorization','keep-alive','te','trailer','forwarded','x-forwarded-host','x-forwarded-for','x-forwarded-proto'])
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.ico':'image/x-icon','.woff2':'font/woff2'}
const limit=11*1024*1024
function fail(res,status,message){if(!res.headersSent){res.writeHead(status,{'Content-Type':'text/plain; charset=utf-8','Cache-Control':'no-store'});res.end(message)}}
const server=http.createServer(async(req,res)=>{
 res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer')
 try{
  let authority;try{authority=new URL('http://'+req.headers.host)}catch{return fail(res,400,'Invalid host')}
  if(!local.has(authority.hostname)&&!allowed.has(authority.hostname))return fail(res,403,'Host is not allowed')
  if(!['GET','HEAD','OPTIONS'].includes(req.method)&&req.headers.origin){let origin;try{origin=new URL(req.headers.origin)}catch{return fail(res,403,'Invalid origin')};if(origin.host!==authority.host)return fail(res,403,'Cross-origin writes are not allowed')}
  const path=req.url.split('?')[0]
  if(path==='/healthz'){
   try{const r=await fetch(new URL('/auth/b/isLogin',backend),{signal:AbortSignal.timeout(5000)});const body=await r.json();if(!r.ok||body.code!==200)throw new Error();res.writeHead(200,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end('{"status":"ready"}')}
   catch{return fail(res,503,'Backend is not ready')};return
  }
  if(path.startsWith('/api/')){
   if(Number(req.headers['content-length']||0)>limit)return fail(res,413,'Upload too large')
   const headers=Object.fromEntries(Object.entries(req.headers).filter(([key])=>!hop.has(key)))
   headers.host=backend.host
   const upstream=http.request({hostname:backend.hostname,port:backend.port||80,method:req.method,path:req.url.slice(4),headers},response=>{
    const clean=Object.fromEntries(Object.entries(response.headers).filter(([key])=>!hop.has(key)))
    clean['cache-control']='no-store';res.writeHead(response.statusCode,clean);response.pipe(res)
   })
   upstream.setTimeout(120000,()=>upstream.destroy(new Error('UPSTREAM_TIMEOUT')))
   upstream.on('error',()=>fail(res,502,'Backend request failed'))
   req.on('aborted',()=>upstream.destroy())
   let size=0;const bounded=new Transform({transform(chunk,encoding,done){size+=chunk.length;if(size>limit){fail(res,413,'Upload too large');upstream.destroy();done(new Error('UPLOAD_LIMIT'));return}done(null,chunk)}})
   bounded.on('error',()=>{req.unpipe(bounded);req.resume()});req.pipe(bounded).pipe(upstream);return
  }
  if(!['GET','HEAD'].includes(req.method))return fail(res,405,'Method not allowed')
  let decoded;try{decoded=decodeURIComponent(path)}catch{return fail(res,400,'Invalid path')}
  if(decoded.split('/').some(part=>part.startsWith('.'))||decoded.includes('\\'))return fail(res,404,'Not found')
  const file=resolve(root,decoded==='/'?'workspace.html':'.'+decoded)
  if(!file.startsWith(root+sep))return fail(res,404,'Not found')
  let actual,info;try{actual=await realpath(file);if(!actual.startsWith(root+sep))return fail(res,404,'Not found');info=await stat(actual);if(!info.isFile())return fail(res,404,'Not found')}catch{return fail(res,404,'Not found')}
  res.writeHead(200,{'Content-Type':mime[extname(actual)]||'application/octet-stream','Content-Length':info.size,'Cache-Control':extname(actual)==='.html'?'no-store':'public, max-age=3600'})
  if(req.method==='HEAD')res.end();else createReadStream(actual).on('error',()=>res.destroy()).pipe(res)
 }catch{fail(res,500,'Review request failed')}
})
server.headersTimeout=15000;server.requestTimeout=120000
server.listen(port,host,()=>console.log('Built review gateway ready on port '+port+'; backend stays private.'))
