"""Real local browser purchase collaboration with four existing accounts only."""
from pathlib import Path
import json,subprocess,time,traceback,sys,urllib.request
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1];bundle=root/'artifacts/SRM-local-review';private=root/'.runtime/collaboration/browser';private.mkdir(parents=True,exist_ok=True)
accounts=json.loads((bundle/'runtime/accounts.json').read_text());cfg=json.load(urllib.request.urlopen('http://localhost:18080/api/dev/config/sysBaseList'))['data'];flags={r:any(x['configKey']=='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_'+r.upper() and str(x['configValue']).lower()=='true' for x in cfg) for r in ['b','c']};stamp=str(int(time.time()));checks=[];stage='start'
def pass_check(s):checks.append(s);print('PASS:',s)
def button(page,name):return page.get_by_role('button',name=name,exact=True)
def no_error(page):
 errors=page.locator('.col-panel [role=alert]')
 if errors.count():raise RuntimeError(errors.first.inner_text())
def wait_ui(page,fn,label):
 for _ in range(35):
  if fn():return
  button(page,'刷新协同').click();page.wait_for_timeout(500);no_error(page)
 raise RuntimeError('Timeout '+label)
try:
 with sync_playwright() as pw:
  browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
  def login(name):
   a=next(a for a in accounts if a['account']==name);realm=a['realm'];ctx=browser.new_context(viewport={'width':1500,'height':1050});page=ctx.new_page();captchas=[]
   def observe(r):
    if '/auth/'+realm+'/getPicCaptcha' in r.url:captchas.append(r.json()['data']['validCodeReqNo'])
   page.on('response',observe);page.goto('http://localhost:18080/?'+('portal' if realm=='c' else 'internal')+'=1');page.get_by_label('登录账号',exact=True).fill(name);page.get_by_label('登录密码',exact=True).fill(a['password'])
   if flags[realm]:
    for _ in range(200):
     if captchas:break
     page.wait_for_timeout(50)
    raw=subprocess.check_output(['docker','compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+captchas[-1]],cwd=bundle,text=True).strip()
    try:code=json.loads(raw)
    except ValueError:code=raw
    page.get_by_label('图片验证码',exact=True).fill(str(code))
   button(page,'登录工作台 →').click();page.get_by_role('heading',level=1,name='寻源与竞价' if realm=='c' else '供应商档案',exact=True).wait_for(timeout=25000);page.get_by_role('button',name='采购协同',exact=False).click();page.get_by_role('heading',level=1,name='采购协同',exact=True).wait_for();page.locator('.col-panel').wait_for();return ctx,page
  stage='tester creates sandbox PO';tc,t=login('lijunhua');button(t,'生成本机沙箱订单').click();t.get_by_label('数量',exact=True).fill('2');t.get_by_label('单价',exact=True).fill('20')
  with t.expect_response(lambda r:r.url.endswith('/collaboration/sandbox/orders') and r.request.method=='POST') as sent:button(t,'生成沙箱订单').click()
  req=sent.value.request.post_data_json;assert sent.value.json()['code']==200;po_no='DEMO-PO-'+req['requestKey'];t.get_by_label('搜索采购订单',exact=True).fill(po_no);button(t,'查询').click();wait_ui(t,lambda:t.locator('tbody tr').filter(has_text=po_no).count()>0,'new PO');pass_check('tester_browser_generates_signed_sandbox_ERP_order')
  stage='supplier confirms PO and creates ASN';pc,p=login('lixiaolong');p.get_by_label('搜索采购订单',exact=True).fill(po_no);button(p,'查询').click();p.locator('tbody tr').filter(has_text=po_no).get_by_role('button',name='查看订单',exact=True).click();button(p,'确认当前订单').click();button(p,'填写发运通知').click();p.get_by_label('ASN编号（可留空）',exact=True).fill('BROWSER-ASN-'+stamp);p.get_by_label('承运商',exact=True).fill('浏览器验收承运商');p.get_by_label('运单号',exact=True).fill('BROWSER-TRACK-'+stamp);button(p,'提交发运通知').click();p.get_by_role('heading',name='BROWSER-ASN-'+stamp,exact=False).wait_for();no_error(p);p.screenshot(path=str(private/'supplier-shipment.png'),full_page=True);pass_check('supplier_browser_confirms_order_and_submits_batched_ASN_tracking')
  stage='buyer receives and creates reconciliation';button(t,'刷新协同').click();t.locator('tbody tr').filter(has_text=po_no).get_by_role('button',name='查看订单',exact=True).click();button(t,'查看发运／收货').click();button(t,'登记人工实收').click();t.get_by_label('收货编号（可留空）',exact=True).fill('BROWSER-RCV-'+stamp);button(t,'保存实收记录').click();button(t,'实收与建账').click();row=t.locator('tbody tr').filter(has_text='BROWSER-RCV-'+stamp);row.get_by_role('checkbox').check();t.get_by_placeholder('对账编号（可留空）',exact=True).fill('BROWSER-REC-'+stamp);button(t,'用选中实收生成对账单').click();button(t,'采购确认').click();no_error(t);pass_check('buyer_browser_posts_actual_receipt_builds_and_confirms_reconciliation')
  stage='supplier confirms reconciliation and registers invoice';button(p,'对账与发票').click();button(p,'刷新协同').click();p.locator('tbody tr').filter(has_text='BROWSER-REC-'+stamp).get_by_role('button',name='查看对账与发票',exact=True).click();button(p,'供应商确认').click();p.get_by_label('发票附件',exact=True).set_input_files({'name':'browser-invoice.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4\nBrowser invoice review\n%%EOF'});button(p,'上传私有附件').click();p.get_by_label('发票号码',exact=True).fill('BROWSER-INV-'+stamp);button(p,'登记发票').click();p.get_by_role('heading',name='发票 BROWSER-INV-'+stamp,exact=False).wait_for();no_error(p);pass_check('supplier_browser_bilateral_confirmation_private_upload_and_invoice_registration')
  stage='finance delivery acknowledgement and simulated payment';button(t,'刷新协同').click();button(t,'推送共享财务').click();wait_ui(t,lambda:t.get_by_role('heading',name='发票 BROWSER-INV-'+stamp+' · 财务已受理',exact=True).count()>0,'finance accepted');button(t,'提交付款申请').click();wait_ui(t,lambda:'付款申请：接口已送达' in t.locator('.col-panel').inner_text(),'payment application');t.get_by_label('模拟付款金额',exact=True).fill('40');button(t,'本机沙箱模拟付款回执').click();wait_ui(t,lambda:t.get_by_role('heading',name='发票 BROWSER-INV-'+stamp+' · 已付款',exact=True).count()>0,'payment');t.screenshot(path=str(private/'tester-paid.png'),full_page=True);pass_check('internal_finance_browser_push_separate_ACK_and_signed_sandbox_payment')
  stage='system administrator integration monitor';ac,a=login('admin');button(a,'集成监控').click();a.get_by_role('heading',name='出站记录',exact=False).wait_for();no_error(a);a.screenshot(path=str(private/'admin-monitor.png'),full_page=True);pass_check('system_admin_browser_sees_safe_inbox_outbox_monitor')
  stage='supplier administrator same-supplier ledger';gc,g=login('gysadmin');button(g,'对账与发票').click();g.locator('tbody tr').filter(has_text='BROWSER-REC-'+stamp).get_by_role('button',name='查看对账与发票',exact=True).click();g.get_by_role('heading',name='发票 BROWSER-INV-'+stamp+' · 已付款',exact=True).wait_for();assert button(g,'推送共享财务').count()==0;assert button(g,'集成监控').count()==0;g.screenshot(path=str(private/'portal-admin-paid.png'),full_page=True);pass_check('portal_admin_browser_reads_own_supplier_invoice_without_internal_controls')
  for ctx in [tc,pc,ac,gc]:ctx.close()
  browser.close()
 (root/'docs/review/W16-19-browser-verification.json').write_text(json.dumps({'result':'passed','date':time.strftime('%Y-%m-%d'),'entry':'http://localhost:18080/','accounts':['lijunhua','lixiaolong','admin','gysadmin'],'checks':checks,'captchaPolicyUnchanged':True,'realExternalSystemsVerified':False,'testRecordsPrefix':'BROWSER-'},ensure_ascii=False,indent=2)+'\n');print('PASS: all four identities and full collaboration browser workflow.')
except Exception as e:
 (private/'error.txt').write_text(traceback.format_exc());print('FAIL:',stage,type(e).__name__,'(private details retained)');sys.exit(1)
