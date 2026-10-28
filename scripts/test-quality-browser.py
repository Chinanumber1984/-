"""P7 actual four-account browser workflow; no additional identities."""
from pathlib import Path
import json,subprocess,time,traceback,sys,urllib.request,re
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1];bundle=root/'artifacts/SRM-local-review';private=root/'.runtime/quality/browser';private.mkdir(parents=True,exist_ok=True);accounts=json.loads((bundle/'runtime/accounts.json').read_text());profile=json.loads((bundle/'runtime/account-profile.json').read_text());probe=json.loads((root/'.runtime/quality/api-probe.json').read_text());cfg=json.load(urllib.request.urlopen('http://localhost:18080/api/dev/config/sysBaseList'))['data'];flags={r:any(x['configKey']=='SNOWY_SYS_DEFAULT_CAPTCHA_OPEN_FLAG_FOR_'+r.upper() and str(x['configValue']).lower()=='true' for x in cfg) for r in ['b','c']};stamp=str(int(time.time()));checks=[];stage='start'
def button(p,name):return p.get_by_role('button',name=name,exact=True)
def done(p,name):checks.append(name);p.screenshot(path=str(private/(name+'.png')),full_page=True);print('PASS:',name)
def no_error(p):
 a=p.locator('.quality-panel [role=alert]')
 if a.count():raise RuntimeError(a.first.inner_text())
try:
 with sync_playwright() as pw:
  browser=pw.chromium.launch(headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
  def login(name):
   account=next(a for a in accounts if a['account']==name);realm=account['realm'];ctx=browser.new_context(viewport={'width':1500,'height':1100},accept_downloads=True);p=ctx.new_page();captchas=[]
   def observe(r):
    if '/auth/'+realm+'/getPicCaptcha' in r.url:captchas.append(r.json()['data']['validCodeReqNo'])
   p.on('response',observe);p.goto('http://localhost:18080/?'+('portal' if realm=='c' else 'internal')+'=1');p.get_by_label('登录账号',exact=True).fill(name);p.get_by_label('登录密码',exact=True).fill(account['password'])
   if flags[realm]:
    for _ in range(200):
     if captchas:break
     p.wait_for_timeout(50)
    raw=subprocess.check_output(['docker','compose','--env-file','runtime/compose.env','exec','-T','redis','redis-cli','-n','1','--raw','GET','Cache:auth-validCode:'+captchas[-1]],cwd=bundle,text=True).strip()
    try:code=json.loads(raw)
    except ValueError:code=raw
    p.get_by_label('图片验证码',exact=True).fill(str(code))
   button(p,'登录工作台 →').click();p.get_by_role('heading',level=1,name='寻源与竞价' if realm=='c' else '供应商档案',exact=True).wait_for(timeout=30000);p.get_by_role('button',name='质量与风险',exact=False).click();p.get_by_role('heading',level=1,name='质量与风险',exact=True).wait_for();return ctx,p
  stage='supplier PPAP draft upload and submit';pc,p=login('lixiaolong');button(p,'新建PPAP').click();p.get_by_label('标题',exact=True).fill('BROWSER-P7-PPAP-'+stamp);button(p,'保存质量单据').click();p.get_by_label('质量附件',exact=True).set_input_files({'name':'browser-ppap.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4\nP7 Browser PPAP\n%%EOF'});button(p,'上传质量私有附件').click();p.get_by_label('提交说明',exact=True).fill('浏览器PPAP第一版');button(p,'提交PPAP审核').click();p.get_by_role('heading',name='BROWSER-P7-PPAP-'+stamp+' · 待SQE审核',exact=True).wait_for();no_error(p);done(p,'supplier_ppap_submit')
  stage='SQE requests PPAP supplement';tc,t=login('lijunhua');button(t,'SQE待办').click();t.locator('.quality-actions').filter(has_text='BROWSER-P7-PPAP-'+stamp).get_by_role('button',name='核查质量资料',exact=True).click();t.get_by_label('SQE审核意见',exact=True).fill('浏览器请补充材料试验报告');button(t,'补件／重做').click();t.get_by_role('heading',name='BROWSER-P7-PPAP-'+stamp+' · 补件／重做',exact=True).wait_for();no_error(t);done(t,'SQE_ppap_supplement_request')
  stage='supplier resubmits PPAP revision';button(p,'刷新质量').click();p.get_by_label('质量附件',exact=True).set_input_files({'name':'browser-ppap-supplement.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4\nP7 Browser PPAP supplement\n%%EOF'});button(p,'上传质量私有附件').click();p.get_by_label('提交说明',exact=True).fill('浏览器补充试验报告并重提第二版');button(p,'提交PPAP审核').click();no_error(p)
  stage='admin approves PPAP as SQE';ac,a=login('admin');button(a,'SQE待办').click();a.locator('.quality-actions').filter(has_text='BROWSER-P7-PPAP-'+stamp).get_by_role('button',name='核查质量资料',exact=True).click();a.get_by_label('SQE审核意见',exact=True).fill('浏览器SQE核验第二版PPAP完整有效');button(a,'审核通过').click();a.get_by_role('heading',name='BROWSER-P7-PPAP-'+stamp+' · PPAP已批准',exact=True).wait_for();done(a,'admin_ppap_second_revision_approved')
  stage='SQE initiates supplier SCAR';button(t,'SCAR / 8D').click();button(t,'发起SCAR').click();t.get_by_label('筛选供应商',exact=True).fill(profile['supplierName']);button(t,'查询质量供应商').click();t.get_by_label('供应商',exact=True).select_option(profile['supplierId']);t.get_by_label('标题',exact=True).fill('BROWSER-P7-SCAR-'+stamp);t.get_by_label('缺陷编码',exact=True).fill('BROWSER-P7-'+stamp);t.get_by_label('缺陷／合规事实',exact=True).fill('浏览器8D演示表面缺陷，非实际质量投诉');button(t,'保存质量单据').click();t.get_by_role('heading',name='BROWSER-P7-SCAR-'+stamp+' · 待8D回复',exact=True).wait_for();no_error(t)
  stage='supplier completes eight structured disciplines';button(p,'SCAR / 8D').click();p.locator('tbody tr').filter(has_text='BROWSER-P7-SCAR-'+stamp).get_by_role('button',name='查看质量单据',exact=True).click();p.get_by_label('质量附件',exact=True).set_input_files({'name':'browser-8d.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4\nP7 Browser 8D proof\n%%EOF'});button(p,'上传质量私有附件').click()
  for i in range(1,9):p.get_by_label(re.compile('^D'+str(i)+' ·')).fill('浏览器 D'+str(i)+' 结构化措施与验证依据')
  button(p,'提交完整8D回复').click();p.get_by_role('heading',name='BROWSER-P7-SCAR-'+stamp+' · 待SQE审核',exact=True).wait_for();done(p,'supplier_structured_D1_D8_reply')
  stage='SQE verifies and confirms effectiveness';button(t,'刷新质量').click();t.get_by_label('SQE审核意见',exact=True).fill('浏览器验证根因和纠正措施通过');button(t,'审核通过').click();t.get_by_label('措施有效性验证结果',exact=True).fill('连续三批验证未复发，措施有效');t.get_by_label('有效性证据引用',exact=True).fill('BROWSER-P7-three-lots-'+stamp);button(t,'确认有效性并闭环SCAR').click();t.get_by_role('heading',name='BROWSER-P7-SCAR-'+stamp+' · 已闭环',exact=True).wait_for();done(t,'SQE_verification_effectiveness_and_SCAR_closure')
  stage='risk assessment and disposition';button(t,'内部风险').click();button(t,'登记内部合规风险').click();t.get_by_label('供应商',exact=True).select_option(profile['supplierId']);t.get_by_label('缺陷／合规事实',exact=True).fill('BROWSER-P7演示风险，非实际黑名单，不对实际测试供方冻结');t.get_by_label('证据引用',exact=True).fill('BROWSER-P7-risk-test-only-'+stamp);button(t,'保存质量单据').click();t.get_by_label('风险等级',exact=True).select_option('HIGH');t.get_by_label('评估／处置意见',exact=True).fill('浏览器演示形成经理冻结建议待办，保持供方状态');button(t,'保存风险评估').click();button(t,'将冻结建议提交经理审批').wait_for();done(t,'risk_high_red_light_and_freeze_suggestion_only');t.get_by_label('处置结果',exact=True).select_option('MONITOR');t.get_by_label('处置证据引用',exact=True).fill('BROWSER-P7-not-real-blacklist-'+stamp);button(t,'归档处置结果').click();no_error(t)
  stage='signed source scoring preview';button(t,'检验与绩效来源').click();t.get_by_label('筛选供应商',exact=True).fill('P7受控冻结验收-');button(t,'查询质量供应商').click();t.get_by_label('来源供应商',exact=True).select_option(probe['sourceSupplierId']);t.get_by_label('绩效月份',exact=True).fill(probe['period']);button(t,'预览来源与扣分').click();button(t,'按当前来源发布并重算绩效').wait_for();button(t,'按当前来源发布并重算绩效').click();t.locator('[role=status]').filter(has_text='已按来源快照生成绩效输入并重算').wait_for();no_error(t);done(t,'sandbox_ERP_QMS_traceable_scoring_browser')
  stage='portal administrator own quality scope';gc,g=login('gysadmin');g.locator('tbody tr').filter(has_text='BROWSER-P7-PPAP-'+stamp).get_by_role('button',name='查看质量单据',exact=True).click();g.get_by_role('heading',name='BROWSER-P7-PPAP-'+stamp+' · PPAP已批准',exact=True).wait_for();assert button(g,'内部风险').count()==0;assert button(g,'SQE待办').count()==0
  with g.expect_download() as download:g.get_by_role('button',name=re.compile('^下载 browser-ppap')).first.click()
  download.value.save_as(private/'portal-admin-quality.pdf');done(g,'portal_admin_own_PPAP_private_download_and_no_internal_controls')
  for ctx in [pc,tc,ac,gc]:ctx.close()
  browser.close()
 (root/'docs/review/W20-22-browser-verification.json').write_text(json.dumps({'result':'passed','date':time.strftime('%Y-%m-%d'),'entry':'http://localhost:18080/','accounts':['lijunhua','admin','lixiaolong','gysadmin'],'checks':checks,'realExternalSystemsVerified':False,'captchaPolicyUnchanged':True},ensure_ascii=False,indent=2)+'\n');print('PASS: P7 four-account real browser workflows complete.')
except Exception as e:
 (private/'error.txt').write_text(traceback.format_exc());print('FAIL:',stage,type(e).__name__,'(private details retained)');sys.exit(1)
