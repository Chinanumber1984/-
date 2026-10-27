"""Actual CSV upload, quote handling, filtered calculation and XLSX download."""
from pathlib import Path
import json,subprocess
from playwright.sync_api import sync_playwright,expect
root=Path(__file__).resolve().parents[1];f=json.loads((root/'.runtime/performance/fixture.json').read_text());checks=[]
# Obtain a real session without printing or persisting the token.
code="import{readFileSync}from'node:fs';import{login}from'./scripts/test-performance-api.mjs';const f=JSON.parse(readFileSync('.runtime/performance/fixture.json'));process.stdout.write(await login(f.owner))"
token=subprocess.check_output(['node','--input-type=module','-e',code],cwd=root,text=True)
with sync_playwright() as p:
 b=p.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage']);page=b.new_page(viewport={'width':1600,'height':1100},locale='zh-CN');errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.add_init_script('sessionStorage.setItem("srm-active-realm","b");sessionStorage.setItem("srm-real-b",'+json.dumps(token)+');')
 page.goto('http://localhost:18080/');expect(page.get_by_role('heading',name='供应商档案',exact=True)).to_be_visible(timeout=20000)
 page.get_by_role('button',name='▥　绩效管理',exact=True).click();expect(page.get_by_role('heading',name='供应商绩效',exact=True)).to_be_visible()
 page.get_by_role('button',name='录入或导入评分',exact=True).click()
 csv='\ufeffsupplierId,period,sourceType,sourceRef,delivery,quality,cost,service\n'+f['supplier']+',2026-06,HISTORICAL,"BROWSER,CSV-LEDGER",60,70,80,90\n'+f['supplier']+',2026-05,SANDBOX,CSV-SANDBOX,75,75,75,75\n'
 page.get_by_label('绩效CSV文件',exact=True).set_input_files({'name':'performance-history.csv','mimeType':'text/csv','buffer':csv.encode()})
 expect(page.get_by_role('dialog',name='录入或导入历史评分',exact=True)).to_contain_text('已解析 2 行');page.get_by_role('button',name='保存来源并计算',exact=True).click();expect(page.get_by_role('dialog',name='录入或导入历史评分',exact=True)).to_have_count(0,timeout=20000);expect(page.locator('.performance-panel')).to_contain_text('已保存来源并计算 2 份月度评分');checks.append('actual_csv_file_upload_with_bom_and_quoted_source_ref')
 page.get_by_label('绩效月份筛选',exact=True).fill('2026-06');page.get_by_role('button',name='查询绩效',exact=True).click();target=page.locator('.performance-panel tbody tr').filter(has_text='绩效验收');expect(target).to_have_count(1);expect(target).to_contain_text('72');
 for row in page.locator('.performance-panel tbody tr').all():expect(row).to_contain_text('2026-06')
 target.get_by_role('button',name='评分明细',exact=True).click();expect(page.get_by_role('dialog',name='月度绩效明细',exact=True)).to_contain_text('BROWSER,CSV-LEDGER');page.get_by_label('关闭表单',exact=True).click();checks.append('bulk_import_calculates_and_preserves_quoted_trace_source')
 with page.expect_download() as download:page.get_by_role('button',name='导出绩效 Excel',exact=True).click()
 download.value.save_as(str(root/'.runtime/performance/csv-filtered-export.xlsx'));checks.append('period_filtered_xlsx_download_from_browser')
 page.get_by_label('绩效供应商',exact=True).select_option(f['supplier']);page.get_by_label('绩效截止月份',exact=True).fill(f['period']);page.get_by_label('绩效趋势月数',exact=True).select_option('6');page.get_by_role('button',name='查看趋势',exact=True).click();expect(page.get_by_role('img',name='近6月绩效趋势',exact=True)).to_be_visible();expect(page.locator('.perf-summary')).to_contain_text('历史不足6个月');checks.append('selectable_trend_window_and_actual_missing_months')
 page.screenshot(path=str(root/'.runtime/performance/csv-import.png'),full_page=True);assert not errors,errors;b.close()
(root/'docs/performance/csv-browser-verification.json').write_text(json.dumps({'result':'passed','entry':'http://localhost:18080/','checks':checks,'runtimeErrors':errors},indent=2)+'\n');print(f'PASS: {len(checks)} CSV browser checks.')
