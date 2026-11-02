from pathlib import Path
import json,os,subprocess,time
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
bundle=Path(os.environ.get('SRM_TEST_BUNDLE',root/'artifacts/SRM-local-review'))
entry=os.environ.get('SRM_TEST_ENTRY','http://localhost:18080/')
accounts=json.loads((bundle/'runtime/accounts.json').read_text())
fixture=json.loads((root/'docs/review/W29-category-scope-api-verification.json').read_text())['retainedFixtures']
checks=[]
with sync_playwright() as pw:
 browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 actor=next(x for x in accounts if x['account']=='admin')
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
 page.get_by_role('button',name='品类字典',exact=False).click();page.get_by_text(fixture['activeCategory'],exact=True).wait_for();page.get_by_text(fixture['disabledCategory'],exact=True).wait_for()
 disabled_row=page.locator('tbody tr').filter(has_text=fixture['disabledCategory']);assert '停用' in disabled_row.inner_text()
 checks.append('category dictionary shows enabled and disabled W29 rows')
 page.get_by_role('button',name='寻源与竞价',exact=False).click();page.get_by_role('button',name='新建询价',exact=True).click();category=page.locator('.sourcing-panel form.src-card select').first
 values=category.locator('option').evaluate_all('(nodes)=>nodes.map(n=>n.value)')
 assert fixture['activeCategory'] in values and fixture['disabledCategory'] not in values,values
 checks.append('new RFQ category selector excludes disabled category')
 page.get_by_role('button',name='取消编辑',exact=True).click();context.close();browser.close()
report={'result':'passed','entry':entry,'date':time.strftime('%Y-%m-%d'),'checks':checks,'mutation':'read-only UI inspection'}
(root/'docs/review/W29-category-scope-browser-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('PASS: W29 category and sourcing scope browser checks')
