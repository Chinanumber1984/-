"""Actual login forms for the four current identities, without printing credentials."""
from pathlib import Path
import json,subprocess,time,traceback,sys,urllib.request
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1];bundle=root/'artifacts/SRM-local-review';private=root/'.runtime/account-profile-verification';private.mkdir(parents=True,exist_ok=True)
accounts=json.loads((bundle/'runtime/accounts.json').read_text());profile=json.loads((bundle/'runtime/account-profile.json').read_text());cfg=json.load(urllib.request.urlopen('http://localhost:18080/api/dev/config/sysBaseList'))['data'];flags={realm:any(c['configKey']=='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_'+realm.upper() and str(c['configValue']).lower()=='true' for c in cfg) for realm in ['b','c']};results=[];stage='browser start'
try:
 with sync_playwright() as pw:
  browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
  for account in accounts:
   realm=account['realm'];stage=account['account']+' login';context=browser.new_context(viewport={'width':1500,'height':1000});page=context.new_page();captcha=[]
   def observe(response):
    if '/auth/'+realm+'/getPicCaptcha' in response.url:captcha.append(response.json()['data']['validCodeReqNo'])
   page.on('response',observe);page.goto('http://localhost:18080/?'+('portal' if realm=='c' else 'internal')+'=1');page.get_by_role('heading',name='供应商门户登录' if realm=='c' else '内部工作台登录',exact=True).wait_for();page.get_by_label('登录账号',exact=True).fill(account['account']);page.get_by_label('登录密码',exact=True).fill(account['password'])
   if flags[realm]:
    for _ in range(200):
     if captcha:break
     page.wait_for_timeout(50)
    if not captcha:raise RuntimeError('Captcha request not ready')
    raw=subprocess.check_output(['docker','compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+captcha[-1]],cwd=bundle,text=True).strip()
    try:code=json.loads(raw)
    except ValueError:code=raw
    page.get_by_label('图片验证码',exact=True).fill(str(code))
   page.get_by_role('button',name='登录工作台 →',exact=True).click();page.get_by_role('heading',name='寻源与竞价' if realm=='c' else '供应商档案',level=1,exact=True).wait_for(timeout=20000)
   if realm=='c':
    stage=account['account']+' invited RFQ';page.locator('tbody tr').filter(has_text=profile['rfqTitle']).first.wait_for(timeout=15000);assert page.get_by_role('button',name='新建询价',exact=True).count()==0
   page.screenshot(path=str(private/(account['account']+'.png')),full_page=True);results.append({'account':account['account'],'realm':realm,'loginFormAndLandingPassed':True,'invitedRfqVisible':realm=='c'});context.close()
  browser.close()
 (root/'docs/review/four-accounts-browser.json').write_text(json.dumps({'result':'passed','date':time.strftime('%Y-%m-%d'),'entry':'http://localhost:18080/','accounts':results,'captchaPolicyUnchanged':True,'passwordsPrinted':False},ensure_ascii=False,indent=2)+'\n');print('PASS: all four real browser logins; both supplier accounts see the published RFQ.')
except Exception as error:
 (private/'browser-error.txt').write_text(traceback.format_exc());print('FAIL:',stage,type(error).__name__,'(private error details retained)');sys.exit(1)
