"""Browser-managed captcha toggle with actual C-realm authentication checks."""
from pathlib import Path
import json,subprocess,time,traceback,sys,urllib.request
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1];bundle=root/'artifacts/SRM-local-review';private=root/'.runtime/portal-login-settings';private.mkdir(parents=True,exist_ok=True);accounts=json.loads((bundle/'runtime/accounts.json').read_text());checks=[];stage='start'
def flag(realm):return next(x['configValue'] for x in json.load(urllib.request.urlopen('http://localhost:18080/api/dev/config/sysBaseList'))['data'] if x['configKey']=='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_'+realm)
def button(p,s):return p.get_by_role('button',name=s,exact=True)
def fill_login(p,name):
 a=next(a for a in accounts if a['account']==name);p.get_by_label('登录账号',exact=True).fill(a['account']);p.get_by_label('登录密码',exact=True).fill(a['password'])
def pass_check(s):checks.append(s);print('PASS:',s)
initial_b=flag('B');initial_c=flag('C')
try:
 with sync_playwright() as pw:
  browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage']);admin_ctx=browser.new_context(viewport={'width':1500,'height':1000});a=admin_ctx.new_page();a.goto('http://localhost:18080/?internal=1');fill_login(a,'admin');button(a,'登录工作台 →').click();a.get_by_role('heading',name='供应商档案',level=1,exact=True).wait_for(timeout=25000);a.locator('aside').get_by_role('button',name='设置',exact=True).click();a.locator('#workspace-settings-menu').get_by_role('button',name='门户登录设置',exact=True).click();a.get_by_role('heading',name='供应商门户登录设置',exact=True).wait_for();switch=a.get_by_role('switch',name='供应商门户图片验证码',exact=True);switch.wait_for()
  def set_flag(on):
   switch.set_checked(on)
   if button(a,'保存门户登录设置').is_enabled():
    with a.expect_response(lambda r:r.url.endswith('/dev/config/editBatch') and r.request.method=='POST') as changed:button(a,'保存门户登录设置').click()
    assert changed.value.json()['code']==200
    a.get_by_role('status').wait_for();assert ('已开启' if on else '已关闭') in a.get_by_role('status').inner_text()
   assert str(flag('C')).lower()==str(on).lower()
  stage='disable through admin UI';set_flag(False);a.screenshot(path=str(private/'admin-captcha-off.png'),full_page=True);pass_check('internal_admin_frontend_switch_persists_C_realm_captcha_off')
  stage='supplier login without captcha';pc=browser.new_context(viewport={'width':1500,'height':1000});p=pc.new_page()
  with p.expect_response(lambda r:r.url.endswith('/dev/config/sysBaseList')):p.goto('http://localhost:18080/?portal=1')
  fill_login(p,'lixiaolong');assert p.get_by_label('图片验证码',exact=True).count()==0
  with p.expect_response(lambda r:r.url.endswith('/auth/c/doLogin') and r.request.method=='POST') as response:button(p,'登录工作台 →').click()
  assert response.value.json()['code']==200;body=response.value.request.post_data_json;assert 'validCode' not in body and 'validCodeReqNo' not in body;p.get_by_role('heading',name='寻源与竞价',level=1,exact=True).wait_for(timeout=20000);assert p.get_by_role('button',name='门户登录设置',exact=False).count()==0;pass_check('supplier_actual_login_succeeds_without_captcha_fields_when_disabled')
  stage='re-enable through admin UI';set_flag(True);enabled_ctx=browser.new_context();enabled=enabled_ctx.new_page();captures=[]
  def observe(r):
   if '/auth/c/getPicCaptcha' in r.url:captures.append(r.json()['data']['validCodeReqNo'])
  enabled.on('response',observe);enabled.goto('http://localhost:18080/?portal=1');enabled.get_by_label('图片验证码',exact=True).wait_for();fill_login(enabled,'lixiaolong')
  for _ in range(200):
   if captures:break
   enabled.wait_for_timeout(50)
  assert enabled.get_by_label('图片验证码',exact=True).get_attribute('required') is not None
  raw=subprocess.check_output(['docker','compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+captures[-1]],cwd=bundle,text=True).strip()
  try:code=json.loads(raw)
  except ValueError:code=raw
  enabled.get_by_label('图片验证码',exact=True).fill(str(code));button(enabled,'登录工作台 →').click();enabled.get_by_role('heading',name='寻源与竞价',level=1,exact=True).wait_for(timeout=20000);pass_check('captcha_can_be_reenabled_and_supplier_login_with_real_captcha_works')
  stage='leave local portal captcha disabled';set_flag(False);assert flag('B')==initial_b;pass_check('final_portal_captcha_off_internal_login_configuration_unchanged')
  for ctx in [admin_ctx,pc,enabled_ctx]:ctx.close()
  browser.close()
 (root/'docs/review/portal-login-settings-browser.json').write_text(json.dumps({'result':'passed','date':time.strftime('%Y-%m-%d'),'entry':'http://localhost:18080/','checks':checks,'initialPortalCaptcha':str(initial_c),'finalPortalCaptcha':'false','internalCaptchaUnchanged':True,'credentialsChanged':False},ensure_ascii=False,indent=2)+'\n')
except Exception as e:
 (private/'browser-error.txt').write_text(traceback.format_exc());print('FAIL:',stage,type(e).__name__,'(private details retained)');sys.exit(1)
