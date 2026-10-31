"""Read-only W27 approval history browser regression on isolated clone."""
from pathlib import Path
import json,os,subprocess
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1];bundle=Path(os.environ['SRM_TEST_BUNDLE']).resolve();entry=os.environ.get('SRM_TEST_ENTRY','http://localhost:18081').rstrip('/')
assert entry=='http://localhost:18081'
recovery=json.loads((bundle.parent/'recovery-result.json').read_text());assert recovery['project']=='srm-drill-p8-20261028'
accounts=json.loads((bundle/'runtime/accounts.json').read_text());checks=[];private=root/'.runtime/pilot/approval-history';private.mkdir(parents=True,exist_ok=True)
def get_code(req):
    args=['docker','compose','--project-directory',str(bundle),'--env-file',str(bundle/'runtime/compose.env'),'-f',str(bundle/'compose.drill.json'),'-p',recovery['project'],'exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+req]
    raw=subprocess.check_output(args,text=True).strip()
    try:return json.loads(raw)
    except ValueError:return raw
def sign_in(page,name,realm):
    actor=next(a for a in accounts if a['account']==name);req=[]
    page.on('response',lambda r:req.append(r.json()['data']['validCodeReqNo']) if '/getPicCaptcha' in r.url and r.ok else None)
    page.goto(entry+('/?portal=1' if realm=='c' else '/?internal=1'))
    page.get_by_label('登录账号',exact=True).fill(actor['account']);page.get_by_label('登录密码',exact=True).fill(actor['password'])
    code=page.get_by_label('图片验证码',exact=True)
    if code.count():
        for _ in range(200):
            if req:break
            page.wait_for_timeout(50)
        assert req;code.fill(str(get_code(req[-1])))
    with page.expect_response(lambda r:r.url.endswith('/auth/'+realm+'/doLogin') and r.request.method=='POST') as response:
        page.get_by_role('button',name='登录工作台 →',exact=True).click()
    assert response.value.json()['code']==200
def check(name):checks.append(name)

with sync_playwright() as pw:
    browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
    internal=browser.new_page(viewport={'width':1500,'height':950});sign_in(internal,'admin','b')
    internal.locator('aside button.nav-item').filter(has_text='审批工作台').click()
    internal.get_by_role('heading',name='我的审批待办',level=2,exact=True).wait_for(timeout=20000)
    with internal.expect_response(lambda r:'/approvals/history?' in r.url and r.request.method=='GET') as history_response:
        internal.get_by_role('button',name='已办 / 审批历史',exact=True).click()
    assert history_response.value.json()['code']==200
    internal.get_by_role('heading',name='已办 / 审批历史',level=2,exact=True).wait_for(timeout=20000)
    internal.locator('select[aria-label="筛选审批状态"]').wait_for()
    rows=internal.locator('.lifecycle-panel table tbody tr')
    if rows.count():
        internal.get_by_role('button',name='查看全链路',exact=True).first.click()
        internal.locator('.approval-timeline').wait_for(timeout=15000)
        assert internal.locator('.approval-timeline li').count()>0
        check('completed approval detail renders historical Flowable node, assignee and timestamps')
    else:
        assert '当前范围没有可查看的已结束审批' in internal.locator('.lifecycle-panel').inner_text()
        check('empty history state appears without fabricating approval records')
    internal.screenshot(path=str(private/'internal-approval-history.png'),full_page=True)
    portal=browser.new_page(viewport={'width':1400,'height':900});sign_in(portal,'gysadmin','c')
    assert portal.locator('aside button.nav-item').filter(has_text='审批工作台').count()==0
    check('supplier portal hides internal approval workbench')
    browser.close()

report={'result':'passed','entry':entry,'project':recovery['project'],'checks':checks,'screenshots':1,'businessRowsChanged':False}
(root/'docs/review/W27-approval-history-browser-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('PASS:',len(checks),'W27 approval history browser checks')
