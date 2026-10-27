import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import {resolve} from 'node:path'
export default defineConfig({
 base:'./',plugins:[vue(),{name:'workspace-root',configureServer(server){server.middlewares.use((req,res,next)=>{if(req.url==='/'){res.writeHead(302,{Location:'/workspace.html'});res.end();return}next()})}}],
 server:{host:'127.0.0.1',port:5175,strictPort:true,proxy:{'/api':{target:'http://127.0.0.1:8080',changeOrigin:true,rewrite:p=>p.replace(/^\/api/,'')}}},
 build:{outDir:'../workspace/build',emptyOutDir:true,rollupOptions:{input:resolve(process.cwd(),'workspace.html')}}
})
