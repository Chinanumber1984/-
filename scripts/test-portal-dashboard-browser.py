"""Read-only W26 portal workbench browser regression on isolated clone."""
from pathlib import Path
import json,os,subprocess,urllib.request
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
bundle=Path(os.environ['SRM_TEST_BUNDLE']).resolve()
entry=os.environ.get('SRM_TEST_ENTRY','http://localhost:18081').rstrip('/')
assert entry=='http://localhost:18081'
recovery=json.loads((bundle.parent/'recovery-result.json').read_text())
assert recovery['project']=='srm-drill-p8-20261028'
accounts=json.loads((bundle/'runtime/accounts.json').read_text())
checks=[]
private=root/'.runtime/pilot/portal-dashboard';private.mkdir(parents=True,exist_ok=True)
def get_code(req):
    args=['docker','compose','--project-directory',str(bundle),'--env-file',str(bundle/'runtime/compose.env'),'-f',str(bundle/'compose.drill.json'),'-p',recovery['project'],'exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+req]
    raw=subprocess.check_output(args,text=True).strip()
    try:return json.loads(raw)
    except ValueError:return raw
def sign_in(page,name):
    user=next(a for a in accounts if a['account']==name);req=[]
    page.on('response',lambda r:req.append(r.json()['data']['validCodeReqNo']) if '/getPicCaptcha' in r.url and r.ok else None)
    page.goto(entry+'/?portal=1')
    page.get_by_label('登录账号',exact=True).fill(user['account'])
    page.get_by_label('登录密码',exact=True).fill(user['password'])
    code=page.get_by_label('图片验证码',exact=True)
    if code.count():
        for _ in range(200):
            if req:break
            page.wait_for_timeout(50)
        assert req
        code.fill(str(get_code(req[-1])))
    with page.expect_response(lambda r:r.url.endswith('/auth/c/doLogin') and r.request.method=='POST') as result:
        page.get_by_role('button',name='登录工作台 →',exact=True).click()
    assert result.value.json()['code']==200
    page.get_by_role('heading',name='供应商工作台',level=1,exact=True).wait_for(timeout=25000)
def pass_check(name):checks.append(name)

with sync_playwright() as pw:
    browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
    portal=browser.new_page(viewport={'width':1500,'height':950});sign_in(portal,'gysadmin')
    portal.locator('.portal-summary-card').first.wait_for(timeout=20000)
    assert portal.locator('.portal-summary-card').count()==4
    portal.get_by_role('button',name='刷新工作台',exact=True).click()
    portal.locator('.portal-dashboard[aria-busy="false"]').wait_for(timeout=20000)
    assert portal.locator('.portal-dashboard [role=alert]').count()==0
    text=portal.locator('.portal-dashboard').inner_text()
    assert '单价' not in text
    assert '￥' not in text and '¥' not in text
    pass_check('supplier workbench is the portal landing page with RFQ, auction, reconciliation and SCAR cards')
    portal.get_by_role('button',name='查看询价',exact=True).click()
    portal.get_by_role('heading',name='寻源与竞价',level=1,exact=True).wait_for(timeout=15000)
    pass_check('RFQ card opens the existing supplier-scoped sourcing workflow')
    portal.locator('aside button.nav-item').filter(has_text='我的工作台').click()
    portal.get_by_role('button',name='查看对账',exact=True).click()
    portal.get_by_role('heading',name='采购协同',level=1,exact=True).wait_for(timeout=15000)
    pass_check('reconciliation card opens the existing collaboration workflow')
    portal.locator('aside button.nav-item').filter(has_text='我的工作台').click()
    portal.get_by_role('button',name='查看质量事项',exact=True).click()
    portal.get_by_role('heading',name='质量与风险',level=1,exact=True).wait_for(timeout=15000)
    pass_check('SCAR card opens the existing quality workflow')
    portal.locator('aside button.nav-item').filter(has_text='我的工作台').click()
    portal.get_by_role('button',name='查看绩效',exact=True).click()
    portal.get_by_role('heading',name='绩效管理',level=1,exact=True).wait_for(timeout=15000)
    pass_check('performance card opens the existing performance workflow')
    portal.screenshot(path=str(private/'portal-workbench.png'),full_page=True)

    limited=browser.new_page(viewport={'width':1400,'height':900});sign_in(limited,'lixiaolong')
    context=limited.evaluate("async()=>{const token=sessionStorage.getItem('srm-real-c');const r=await fetch('/api/client/c/srm/v1/session/context',{headers:{token}});return (await r.json()).data}")
    roles=set(context['roles'])
    expected={'查看询价':bool(roles & {'ADMIN','QUOTE'}),'查看对账':bool(roles & {'ADMIN','SHIP','FINANCE'}),'查看质量事项':bool(roles & {'ADMIN','QUALITY'}),'查看绩效':bool(roles & {'ADMIN','QUOTE','QUALITY','FINANCE'})}
    for label,enabled in expected.items():
        button=limited.get_by_role('button',name=label,exact=True)
        assert button.is_enabled()==enabled,(label,roles)
    pass_check('portal cards follow the signed-in contact role set')
    browser.close()

report={'result':'passed','entry':entry,'project':recovery['project'],'checks':checks,'screenshots':1,'savedSettingsChanged':False}
(root/'docs/review/W26-portal-dashboard-browser-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('PASS:',len(checks),'W26 portal workbench browser checks')
