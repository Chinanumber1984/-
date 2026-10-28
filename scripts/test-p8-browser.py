"""Browser checks against the P8 isolated restore; never toggles saved settings."""
from pathlib import Path
import json,os,subprocess,urllib.request
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
bundle=Path(os.environ['SRM_TEST_BUNDLE']).resolve()
entry=os.environ['SRM_TEST_ENTRY'].rstrip('/')
assert entry=='http://localhost:18081'
report=json.loads((bundle.parent/'recovery-result.json').read_text())
assert report['project']=='srm-drill-p8-20261028' and report['result']=='passed'
users=json.loads((bundle/'runtime/accounts.json').read_text())
checks=[]
private=root/'.runtime/pilot/browser';private.mkdir(parents=True,exist_ok=True)
def captcha(realm,req):
    cfg=json.load(urllib.request.urlopen(entry+'/api/dev/config/sysBaseList'))['data']
    flag=next(x['configValue'] for x in cfg if x['configKey']=='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_'+realm.upper())
    if str(flag).lower()!='true':return None
    raw=subprocess.check_output(['docker','compose','--project-directory',str(bundle),'--env-file',str(bundle/'runtime/compose.env'),'-f',str(bundle/'compose.drill.json'),'-p',report['project'],'exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+req],text=True).strip()
    try:return json.loads(raw)
    except ValueError:return raw
def sign_in(page,account,realm):
    user=next(x for x in users if x['account']==account)
    page.goto(entry+('/?portal=1' if realm=='c' else '/?internal=1'))
    page.get_by_label('登录账号',exact=True).fill(user['account'])
    page.get_by_label('登录密码',exact=True).fill(user['password'])
    code_input=page.get_by_label('图片验证码',exact=True)
    if code_input.count():
        req=[]
        page.on('response',lambda r:req.append(r.json()['data']['validCodeReqNo']) if '/getPicCaptcha' in r.url and r.ok else None)
        page.reload()
        page.get_by_label('登录账号',exact=True).fill(user['account'])
        page.get_by_label('登录密码',exact=True).fill(user['password'])
        for _ in range(100):
            if req:break
            page.wait_for_timeout(50)
        assert req
        code_input=page.get_by_label('图片验证码',exact=True)
        code_input.fill(str(captcha(realm,req[-1])))
    with page.expect_response(lambda r:r.url.endswith('/auth/'+realm+'/doLogin') and r.request.method=='POST') as response:
        page.get_by_role('button',name='登录工作台 →',exact=True).click()
    assert response.value.json()['code']==200

with sync_playwright() as pw:
    browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
    admin=browser.new_page(viewport={'width':1440,'height':900})
    sign_in(admin,'admin','b')
    admin.get_by_role('heading',name='供应商档案',level=1,exact=True).wait_for(timeout=25000)
    aside=admin.locator('aside')
    aside.get_by_role('button',name='设置',exact=True).click()
    menu=admin.locator('#workspace-settings-menu')
    menu.get_by_role('button',name='门户登录设置',exact=True).click()
    admin.get_by_role('heading',name='供应商门户登录设置',exact=True).wait_for()
    switch=admin.get_by_role('switch',name='供应商门户图片验证码',exact=True)
    switch.wait_for()
    assert switch.is_checked()==(str(captcha_flag).lower()=='true') if (captcha_flag:=next(x['configValue'] for x in json.load(urllib.request.urlopen(entry+'/api/dev/config/sysBaseList'))['data'] if x['configKey']=='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_C')) else False
    admin.screenshot(path=str(private/'admin-settings.png'),full_page=True)
    checks.append('admin sees Settings > Portal Login Settings; saved captcha state unchanged')
    tester=browser.new_page(viewport={'width':1440,'height':900})
    sign_in(tester,'lijunhua','b')
    tester.get_by_role('heading',name='供应商档案',level=1,exact=True).wait_for(timeout=25000)
    assert tester.locator('aside').get_by_role('button',name='设置',exact=True).count()==0
    checks.append('ordinary internal tester does not see admin Settings menu')
    portal=browser.new_page(viewport={'width':1440,'height':900})
    sign_in(portal,'lixiaolong','c')
    portal.get_by_role('heading',name='寻源与竞价',level=1,exact=True).wait_for(timeout=25000)
    assert portal.get_by_role('button',name='门户登录设置',exact=False).count()==0
    checks.append('supplier portal reaches sourcing workspace without internal settings')
    portal.screenshot(path=str(private/'supplier-workspace.png'),full_page=True)
    browser.close()
out={'result':'passed','entry':entry,'project':report['project'],'checks':checks,'settingsChanged':False,'screenshots':2}
dest=root/'docs/review/P8-browser-verification.json';dest.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
print('PASS:',len(checks),'isolated browser checks; no configuration changed')
