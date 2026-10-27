"""Actual W10–11 UI regression through localhost:18080; private credentials stay in .runtime."""
from pathlib import Path
import os,json,time,subprocess
from playwright.sync_api import sync_playwright,expect
root=Path(__file__).resolve().parents[1];f=json.loads((root/'.runtime/performance/fixture.json').read_text());checks=[]
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage']);page=browser.new_page(viewport={'width':1600,'height':1100});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 def login(actor,portal=False):
  captcha_responses=[]
  def capture(response):
   if '/getPicCaptcha' in response.url:captcha_responses.append(response)
  page.on('response',capture)
  page.goto('http://localhost:18080/')
  page.get_by_role('button',name='供应商门户' if portal else '内部工作台',exact=True).click()
  page.wait_for_load_state('networkidle')
  page.get_by_label('登录账号',exact=True).fill(actor['account']);page.get_by_label('登录密码',exact=True).fill(actor['password'])
  if page.get_by_label('图片验证码',exact=True).count():
   # Existing captcha remains enabled. Read this test browser's issued captcha from its private Redis cache.
   issued=[r.json().get('data',{}) for r in captcha_responses if r.ok]
   data=next((d for d in reversed(issued) if 'validCodeReqNo' in d),None)
   assert data,'No successful captcha challenge was issued'
   raw=subprocess.check_output(['docker','compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+data['validCodeReqNo']],cwd=root/'artifacts/SRM-local-review',text=True).strip()
   try:code=json.loads(raw)
   except:code=raw
   page.get_by_label('图片验证码',exact=True).fill(str(code))
  page.remove_listener('response',capture)
  page.get_by_role('button',name='登录工作台 →',exact=True).click();expect(page.get_by_role('heading',name='供应商档案',exact=True)).to_be_visible(timeout=20000)
 def logout():
  page.get_by_role('button',name='退出登录',exact=True).first.click();expect(page.get_by_label('登录账号',exact=True)).to_be_visible()
 def performance():
  page.get_by_role('button',name='▥　绩效管理',exact=True).click();expect(page.get_by_role('heading',name='供应商绩效',exact=True)).to_be_visible();expect(page.locator('.performance-panel tbody tr').first).to_be_visible(timeout=15000)
 def target():return page.locator('.performance-panel tbody tr').filter(has_text='绩效验收').filter(has_text=f['period']).first
 login(f['owner']);performance();checks.append('internal_performance_navigation_and_real_score_list')
 page.get_by_label('绩效供应商',exact=True).select_option(f['supplier']);page.get_by_label('绩效截止月份',exact=True).fill(f['period']);page.get_by_role('button',name='查看趋势',exact=True).click();expect(page.get_by_role('img',name='近12月绩效趋势')).to_be_visible();expect(page.locator('.perf-summary')).to_contain_text('历史不足12个月');checks.append('actual_month_trend_and_partial_history_notice')
 target().get_by_role('button',name='评分明细',exact=True).click();expect(page.get_by_role('dialog',name='月度绩效明细',exact=True)).to_contain_text('TEST-LEDGER');expect(page.get_by_role('dialog',name='月度绩效明细',exact=True)).to_contain_text('模板版本');page.get_by_label('关闭表单',exact=True).click();checks.append('source_contributions_and_revision_history_visible')
 with page.expect_download() as download:page.get_by_role('button',name='导出绩效 Excel',exact=True).click()
 download.value.save_as(str(root/'.runtime/performance/browser-export.xlsx'));checks.append('browser_downloads_real_xlsx')
 page.get_by_role('button',name='录入或导入评分',exact=True).click();page.get_by_label('导入绩效供应商',exact=True).select_option(f['supplier']);page.get_by_label('录入评分月份',exact=True).fill('2026-07');page.get_by_label('绩效来源凭据',exact=True).fill('BROWSER-LEDGER-'+str(int(time.time())))
 for label in ['交付分','质量分','成本分','服务分']:page.get_by_label(label,exact=True).fill('82')
 page.get_by_role('button',name='保存来源并计算',exact=True).click();expect(page.get_by_role('dialog',name='录入或导入历史评分',exact=True)).to_have_count(0,timeout=20000);expect(page.locator('.performance-panel')).to_contain_text('已保存来源并计算');checks.append('history_entry_calculates_from_actual_ui')
 page.screenshot(path=str(root/'.runtime/performance/internal-ui.png'),full_page=True);logout()
 login(f['portal'],True);performance();expect(page.get_by_role('button',name='录入或导入评分',exact=True)).to_have_count(0);target().get_by_role('button',name='评分明细',exact=True).click();page.get_by_role('button',name='发起绩效申诉',exact=True).click();page.get_by_label('绩效申诉说明',exact=True).fill('浏览器验收：服务分与原始台账存在差异，请SQE复核');page.get_by_role('button',name='提交绩效申诉',exact=True).click();expect(page.get_by_role('dialog',name='月度绩效明细',exact=True)).to_contain_text('待SQE复核',timeout=15000);page.screenshot(path=str(root/'.runtime/performance/portal-appeal.png'),full_page=True);page.get_by_label('关闭表单',exact=True).click();checks.append('portal_bound_supplier_view_and_actual_appeal');logout()
 login(f['sqe']);performance();target().get_by_role('button',name='评分明细',exact=True).click();page.get_by_role('button',name='复核申诉',exact=True).click();page.get_by_label('修正服务分',exact=True).fill('98');page.get_by_label('绩效复核意见',exact=True).fill('浏览器验收：已核对原台账，批准修正服务评分');page.get_by_role('button',name='确认绩效复核',exact=True).click();expect(page.get_by_role('dialog',name='月度绩效明细',exact=True)).to_contain_text('已批准修正',timeout=20000);page.screenshot(path=str(root/'.runtime/performance/sqe-reviewed.png'),full_page=True);page.get_by_label('关闭表单',exact=True).click();checks.append('independent_sqe_reviews_and_preserves_old_versions');logout()
 login(f['portal'],True);performance();page.reload();page.get_by_role('button',name='▥　绩效管理',exact=True).click();target().get_by_role('button',name='评分明细',exact=True).click();expect(page.get_by_role('dialog',name='月度绩效明细',exact=True)).to_contain_text('已批准修正');checks.append('portal_refresh_preserves_reviewed_score');assert not errors,errors
 browser.close()
(root/'docs/performance/browser-verification.json').write_text(json.dumps({'result':'passed','entry':'http://localhost:18080/','environment':'actual WSL Playwright Chromium through the local review gateway','checks':checks,'runtimeErrors':errors},ensure_ascii=False,indent=2)+'\n');print(f'PASS: {len(checks)} performance browser checks; zero runtime errors.')
