"""W30 browser smoke on the isolated review clone; verifies the rendered automatic approval state."""
from pathlib import Path
import json,subprocess,urllib.request
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
bundle=Path(__import__('os').environ['SRM_TEST_BUNDLE'])
entry=__import__('os').environ.get('SRM_TEST_ENTRY','')
if entry!='http://localhost:18081':raise SystemExit('W30 browser check requires isolated clone localhost:18081')
accounts=json.loads((root/'artifacts/SRM-local-review/runtime/accounts.json').read_text())
cfg=json.load(urllib.request.urlopen(entry+'/api/dev/config/sysBaseList'))['data']
captcha=any(x['configKey']=='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_B' and str(x['configValue']).lower()=='true' for x in cfg)
checks=[]
with sync_playwright() as pw:
 browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 page=browser.new_page(viewport={'width':1500,'height':1000})
 captchas=[]
 page.on('response',lambda r:captchas.append(r.json()['data']['validCodeReqNo']) if '/auth/b/getPicCaptcha' in r.url else None)
 page.goto(entry+'/?internal=1')
 page.get_by_label('登录账号',exact=True).fill('lijunhua')
 actor=next(a for a in accounts if a['account']=='lijunhua')
 page.get_by_label('登录密码',exact=True).fill(actor['password'])
 if captcha:
  page.wait_for_timeout(500)
  req=captchas[-1]
  raw=subprocess.check_output(['docker','compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+req],cwd=bundle,text=True).strip()
  try:code=json.loads(raw)
  except ValueError:code=raw
  page.get_by_label('图片验证码',exact=True).fill(str(code))
 page.get_by_role('button',name='登录工作台 →',exact=True).click()
 page.get_by_role('heading',level=1,name='供应商档案',exact=True).wait_for(timeout=30000)
 page.get_by_role('button',name='质量与风险',exact=False).click()
 page.get_by_role('heading',level=1,name='质量与风险',exact=True).wait_for()
 page.get_by_role('button',name='内部风险',exact=True).click()
 rows=page.locator('.quality-panel table tbody tr')
 rows.first.wait_for(timeout=20000)
 rows.first.get_by_role('button',name='查看风险',exact=True).click()
 note=page.locator('.quality-panel .info-note').filter(has_text='高风险冻结建议')
 note.wait_for(timeout=10000)
 text=note.inner_text()
 if '已进入审批：' not in text:raise AssertionError('Rendered risk detail does not show its original approval ID: '+text)
 checks.append('quality_panel_renders_high_risk_original_freeze_approval')
 if page.get_by_role('button',name='将冻结建议提交经理审批',exact=True).count():raise AssertionError('Already-submitted risk still offers duplicate submit action')
 checks.append('duplicate_manual_submit_action_hidden_after_automatic_submission')
 out=root/'docs/review/W30-risk-freeze-browser-verification.json'
 out.write_text(json.dumps({'result':'passed','entry':entry,'checks':checks,'scope':'isolated clone only'},ensure_ascii=False,indent=2)+'\n')
 print('PASS:',len(checks),'W30 risk-freeze browser checks')
 browser.close()
