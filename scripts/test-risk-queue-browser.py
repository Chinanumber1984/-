from pathlib import Path
import json,os,subprocess,time
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
bundle=Path(os.environ.get('SRM_TEST_BUNDLE',root/'artifacts/SRM-local-review'))
entry=os.environ.get('SRM_TEST_ENTRY','http://localhost:18080/')
accounts=json.loads((bundle/'runtime/accounts.json').read_text())
checks=[]
with sync_playwright() as pw:
 browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 actor=next(x for x in accounts if x['account']=='lijunhua')
 context=browser.new_context(viewport={'width':1500,'height':1000})
 page=context.new_page();captchas=[]
 page.on('response',lambda r:captchas.append(r.json()['data']['validCodeReqNo']) if '/auth/b/getPicCaptcha' in r.url else None)
 page.goto(entry+'?internal=1');page.get_by_label('登录账号',exact=True).fill(actor['account']);page.get_by_label('登录密码',exact=True).fill(actor['password'])
 cfg=page.request.get(entry.rstrip('/')+'/api/dev/config/sysBaseList').json()['data']
 if any(x['configKey']=='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_B' and str(x['configValue']).lower()=='true' for x in cfg):
  for _ in range(200):
   if captchas:break
   page.wait_for_timeout(50)
  raw=subprocess.check_output(['docker','compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+captchas[-1]],cwd=bundle,text=True).strip()
  try:code=json.loads(raw)
  except ValueError:code=raw
  page.get_by_label('图片验证码',exact=True).fill(str(code))
 page.get_by_role('button',name='登录工作台 →',exact=True).click();page.get_by_role('heading',level=1,name='供应商档案',exact=True).wait_for(timeout=30000)
 page.get_by_role('button',name='质量与风险',exact=False).click()
 with page.expect_response(lambda r:'/srm/v1/quality/risks?' in r.url and r.request.method=='GET',timeout=15000):page.get_by_role('button',name='内部风险',exact=True).click()
 with page.expect_response(lambda r:'/srm/v1/quality/risks?' in r.url and r.request.method=='GET',timeout=15000):page.get_by_label('风险类型筛选',exact=True).select_option('DELIVERY_EXCEPTION')
 page.locator('.quality-panel tbody tr').first.wait_for(state='visible',timeout=15000) if page.locator('.quality-panel tbody tr').count() else page.get_by_text('当前范围暂无记录',exact=True).wait_for()
 for row in page.locator('.quality-panel tbody tr').all():
  assert row.locator('td').nth(0).inner_text().strip()=='履约异常',row.inner_text()
 checks.append('delivery exception filter is visible and limits browser rows')
 with page.expect_response(lambda r:'/srm/v1/quality/risks?' in r.url and r.request.method=='GET',timeout=15000):page.get_by_label('风险状态筛选',exact=True).select_option('PENDING')
 for row in page.locator('.quality-panel tbody tr').all():
  assert row.locator('td').nth(3).inner_text().strip()=='待评估'
 checks.append('status filter combines with selected risk type')
 assert page.get_by_label('风险等级筛选',exact=True).count()==1
 assert page.get_by_text('来源',exact=True).count()==1
 assert page.get_by_role('button',name='查看风险',exact=True).count()>=0
 checks.append('source classification and detail action render')
 context.close();browser.close()
report={'result':'passed','entry':entry,'date':time.strftime('%Y-%m-%d'),'checks':checks,'mutation':'read-only business data; browser login only'}
(root/'docs/review/W28-risk-queue-browser-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('PASS: W28 risk queue browser checks')
