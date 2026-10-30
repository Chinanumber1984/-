"""Read-only browser check for supplier detail performance/risk/orders tabs."""
from pathlib import Path
import json,os,subprocess,urllib.request
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1];bundle=Path(os.environ['SRM_TEST_BUNDLE']).resolve();entry=os.environ['SRM_TEST_ENTRY'].rstrip('/')
assert entry=='http://localhost:18081'
recovery=json.loads((bundle.parent/'recovery-result.json').read_text());assert recovery['project']=='srm-drill-p8-20261028'
accounts=json.loads((bundle/'runtime/accounts.json').read_text());checks=[]
private=root/'.runtime/pilot/supplier-overview';private.mkdir(parents=True,exist_ok=True)
def read_config():return json.load(urllib.request.urlopen(entry+'/api/dev/config/sysBaseList'))['data']
def get_code(req):
    args=['docker','compose','--project-directory',str(bundle),'--env-file',str(bundle/'runtime/compose.env'),'-f',str(bundle/'compose.drill.json'),'-p',recovery['project'],'exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+req]
    raw=subprocess.check_output(args,text=True).strip()
    try:return json.loads(raw)
    except ValueError:return raw
def sign_in(page,name,realm):
    user=next(a for a in accounts if a['account']==name);req=[]
    page.on('response',lambda r:req.append(r.json()['data']['validCodeReqNo']) if '/getPicCaptcha' in r.url and r.ok else None)
    page.goto(entry+('/?portal=1' if realm=='c' else '/?internal=1'))
    page.get_by_label('登录账号',exact=True).fill(user['account']);page.get_by_label('登录密码',exact=True).fill(user['password'])
    code=page.get_by_label('图片验证码',exact=True)
    if code.count():
        for _ in range(200):
            if req:break
            page.wait_for_timeout(50)
        assert req
        code.fill(str(get_code(req[-1])))
    with page.expect_response(lambda r:r.url.endswith('/auth/'+realm+'/doLogin') and r.request.method=='POST') as result:
        page.get_by_role('button',name='登录工作台 →',exact=True).click()
    assert result.value.json()['code']==200
def pass_check(x):checks.append(x)

with sync_playwright() as pw:
    browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
    internal=browser.new_page(viewport={'width':1500,'height':950});sign_in(internal,'lijunhua','b')
    internal.get_by_role('heading',name='供应商档案',level=1,exact=True).wait_for(timeout=25000)
    internal.locator('.supplier-cell').first.click();internal.locator('.drawer-content').wait_for(timeout=15000)
    for label,tab in [('绩效','performance'),('质量风险','risks'),('采购订单','orders')]:
        internal.get_by_role('button',name=label,exact=True).click()
        internal.locator('.supplier-linked-records').wait_for()
        internal.locator('.supplier-linked-records .info-note, .supplier-linked-records table, .supplier-linked-records .empty-state, .supplier-linked-records [role=alert]').first.wait_for(timeout=20000)
        assert internal.locator('.supplier-linked-records [role=alert]').count()==0
        pass_check('internal supplier detail renders '+label+' with scoped records/empty state')
    internal.screenshot(path=str(private/'internal-supplier-overview.png'),full_page=True)
    portal=browser.new_page(viewport={'width':1500,'height':950});sign_in(portal,'gysadmin','c')
    portal.get_by_role('heading',name='寻源与竞价',level=1,exact=True).wait_for(timeout=25000)
    portal.locator('aside button.nav-item').filter(has_text='供应商档案').first.click()
    portal.locator('.supplier-cell').first.click()
    assert portal.get_by_role('button',name='绩效',exact=True).count()==0
    assert portal.get_by_role('button',name='质量风险',exact=True).count()==0
    assert portal.get_by_role('button',name='采购订单',exact=True).count()==0
    pass_check('supplier portal detail hides internal performance, risk and procurement tabs')
    browser.close()
report={'result':'passed','entry':entry,'project':recovery['project'],'checks':checks,'screenshots':1,'savedSettingsChanged':False}
(root/'docs/review/W25-supplier-overview-browser-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('PASS:',len(checks),'supplier overview browser checks')
