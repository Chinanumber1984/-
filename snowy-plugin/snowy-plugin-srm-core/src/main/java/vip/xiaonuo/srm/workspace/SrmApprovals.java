package vip.xiaonuo.srm.workspace;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import cn.hutool.json.JSONUtil;
import cn.hutool.crypto.digest.DigestUtil;
import vip.xiaonuo.flow.api.*;
import vip.xiaonuo.srm.foundation.SensitiveFieldCodec;
import vip.xiaonuo.sys.api.SysUserApi;
import vip.xiaonuo.dev.api.DevMessageApi;
import static vip.xiaonuo.srm.workspace.SrmWorkspaceService.*;
import static vip.xiaonuo.srm.workspace.SrmApprovalInputs.*;

@Service @RequiredArgsConstructor
public class SrmApprovals {
 private final JdbcTemplate db;private final SrmWorkspaceService suppliers;private final SrmFlowApi flow;private final SensitiveFieldCodec codec;private final SysUserApi users;private final DevMessageApi messages;
 @jakarta.annotation.Resource(name="loginUserApi") private vip.xiaonuo.auth.api.SaBaseLoginUserApi authUsers;
 @Value("${srm.approval.correction-limit:3}") private int correctionLimit;
 @Value("${srm.approval.eliminate-at-limit:true}") private boolean eliminateAtLimit;
 @Value("${srm.approval.initial-tier:QUALIFIED}")private String initialTier;
 static final Set<String> KINDS=Set.of("ONBOARD","CHANGE","FREEZE","UNFREEZE","ELIMINATE");
 private boolean pending(String sid){return db.queryForObject("SELECT COUNT(*) FROM SRM_APPROVAL_LINK WHERE SUPPLIER_ID=? AND STATUS='PENDING'",Long.class,sid)>0;}
 private Map<String,Object> link(String id,boolean lock){var r=db.queryForList("SELECT * FROM SRM_APPROVAL_LINK WHERE ID=?"+(lock?" FOR UPDATE":""),id);require(r.size()==1,404,"申请不存在");return r.get(0);}
 private String clear(Object c){return c==null?"":codec.decrypt(String.valueOf(c));}
 @Transactional public Map<String,Object> submit(SrmActor a,String sid,Submit p){
  require(KINDS.contains(p.kind()),400,"不支持的申请类型");if(Set.of("ONBOARD","CHANGE").contains(p.kind()))a.requireManage();else require(a.canClassify(),403,"生命周期申请仅采购经理可发起");
  var s=suppliers.supplier(a,sid,true);String hash=DigestUtil.sha256Hex(JSONUtil.toJsonStr(new com.fasterxml.jackson.databind.ObjectMapper().convertValue(p,Map.class)));
  var prior=db.queryForList("SELECT * FROM SRM_APPROVAL_LINK WHERE ORG_ID=? AND REQUEST_KEY=?",s.get("ORG_ID"),p.requestKey());
  if(!prior.isEmpty()){var old=prior.get(0);require(sid.equals(old.get("SUPPLIER_ID"))&&a.userId().equals(old.get("REQUESTED_BY"))&&hash.equals(old.get("REQUEST_HASH")),409,"幂等键已用于其他请求");return detail(a,str(old,"ID"));}
  require(ver(s)==p.rowVersion(),409,"档案已变更，请刷新");require(!pending(sid),409,"已有进行中的申请");String state=str(s,"STATUS");
  require(!state.equals("ELIMINATED"),409,"淘汰为终态，不接受新申请");
  switch(p.kind()){
   case "ONBOARD" -> {require(Set.of("POTENTIAL","CORRECTION").contains(state),409,"仅潜在或补正中的供应商可提交准入");require(correctionLimit>0&&correctionLimit<=20,500,"补正上限配置无效");var q=suppliers.qualification(s);require(Boolean.TRUE.equals(q.get("complete")),400,"缺失或过期资质："+q.get("missing"));require(db.queryForObject("SELECT COUNT(*) FROM SRM_SUPPLIER_CONTACT WHERE SUPPLIER_ID=? AND ENABLED=true AND DELETE_FLAG='NOT_DELETE' AND JSON_CONTAINS(ROLES,'\"ADMIN\"')",Long.class,sid)>0,400,"请先补充有效管理员联系人");}
   case "CHANGE" -> {require(Set.of("QUALIFIED","FROZEN").contains(state)&&p.changes()!=null,409,"仅合格或冻结供应商可申请资料变更");validateChanges(s,p.changes());}
   case "FREEZE" -> require(state.equals("QUALIFIED"),409,"仅合格供应商可申请冻结");
   case "UNFREEZE" -> require(state.equals("FROZEN"),409,"仅冻结供应商可申请解冻");
   case "ELIMINATE" -> require(Set.of("POTENTIAL","CORRECTION","QUALIFIED","FROZEN").contains(state),409,"当前状态不能申请淘汰");
  }
  if(!p.kind().equals("ONBOARD"))require(p.reason()!=null&&!p.reason().isBlank(),400,"请填写申请原因");
  require(flow.definitions().stream().filter(d->d.get("key").equals("srm_"+p.kind().toLowerCase(Locale.ROOT))).findFirst().map(d->!Boolean.TRUE.equals(d.get("suspended"))).orElse(false),409,"流程未发布或已挂起，请联系流程管理员");
  String aid=id();String pid=flow.start("srm_"+p.kind().toLowerCase(Locale.ROOT),aid,Map.of("approvalId",aid,"supplierId",sid,"orgId",str(s,"ORG_ID"),"requestedBy",a.userId(),"businessType",p.kind()));
  db.update("UPDATE SRM_SUPPLIER SET STATUS=?,ROW_VERSION=ROW_VERSION+1,UPDATE_TIME=NOW(),UPDATE_USER=? WHERE ID=?",p.kind().equals("ONBOARD")?"REVIEWING":state,a.userId(),sid);
  String payload=JSONUtil.toJsonStr(Map.of("reason",p.reason()==null?"":p.reason(),"changes",p.changes()==null?Map.of():new com.fasterxml.jackson.databind.ObjectMapper().convertValue(p.changes(),Map.class)));
  db.update("INSERT INTO SRM_APPROVAL_LINK(ID,ORG_ID,SUPPLIER_ID,BUSINESS_TYPE,BUSINESS_ID,PROCESS_INSTANCE_ID,STATUS,ROW_VERSION,CREATED_AT,REQUEST_KEY,REQUEST_HASH,REQUESTED_BY,BASE_VERSION,PAYLOAD_CIPHER,SNAPSHOT_CIPHER) VALUES(?,?,?,?,?,?,'PENDING',0,NOW(3),?,?,?,?,?,?)",aid,s.get("ORG_ID"),sid,p.kind(),aid,pid,p.requestKey(),hash,a.userId(),ver(s)+1,codec.encrypt(payload),codec.encrypt(JSONUtil.toJsonStr(Map.of("name",str(s,"NAME"),"category",str(s,"CATEGORY"),"address",str(s,"ADDRESS"),"bankName",str(s,"BANK_NAME"),"creditCode",clear(s.get("CREDIT_CODE")),"bankAccount",clear(s.get("BANK_ACCOUNT")),"status",state))));
  suppliers.audit(a,sid,aid,"APPROVAL_SUBMITTED",Map.of("kind",p.kind(),"processId",pid));notifyTasks(link(aid,false));return detail(a,aid);
 }
 private void validateChanges(Map<String,Object> s,SrmInputs.SupplierEdit p){String credit=p.creditCode()==null||p.creditCode().isBlank()?clear(s.get("CREDIT_CODE")):validCredit(p.creditCode());require(db.queryForObject("SELECT COUNT(*) FROM SRM_CATEGORY WHERE CODE=? AND ENABLED=true",Long.class,p.category())==1,400,"品类不存在或已停用");require(db.queryForObject("SELECT COUNT(*) FROM SRM_SUPPLIER WHERE ORG_ID=? AND CREDIT_CODE_FINGERPRINT=? AND ID<>?",Long.class,s.get("ORG_ID"),codec.creditFingerprint(str(s,"ORG_ID"),credit),s.get("ID"))==0,409,"信用代码已存在");}
 private String role(Map<String,Object> t){return "sqe".equals(t.get("node"))?"SQE":"CAT-MGR";}
 private boolean canTask(SrmActor a,Map<String,Object> l,Map<String,Object> t){
  if(a.portal()||a.userId().equals(l.get("REQUESTED_BY")))return false;
  suppliers.supplier(a,str(l,"SUPPLIER_ID"),false);String assigned=str(t,"assignee");if(!assigned.isBlank()&&!assigned.equals(a.userId()))return false;
  String nodeRole=role(t);if("ADD_SIGN".equals(t.get("node"))){var parent=flow.task(str(t,"parentTaskId"));nodeRole=role(parent);}
  return a.superUser()||a.roles().contains(nodeRole);
 }
 public List<Map<String,Object>> inbox(SrmActor a){require(!a.portal(),403,"门户无内部审批待办");var result=new ArrayList<Map<String,Object>>();var args=new ArrayList<Object>();String scope=suppliers.where(a,args).replaceAll("\\bORG_ID\\b","s.ORG_ID").replaceAll("\\bID\\b","s.ID").replaceAll("\\bCREATE_USER\\b","s.CREATE_USER");for(var l:db.queryForList("SELECT l.* FROM SRM_APPROVAL_LINK l JOIN SRM_SUPPLIER s ON s.ID=l.SUPPLIER_ID WHERE l.STATUS='PENDING' AND s.DELETE_FLAG='NOT_DELETE' AND "+scope+" ORDER BY l.CREATED_AT DESC LIMIT 1000",args.toArray())){
   try{for(var t:flow.tasks(str(l,"PROCESS_INSTANCE_ID")))if(canTask(a,l,t)){var r=new LinkedHashMap<String,Object>(t);var supplier=suppliers.supplier(a,str(l,"SUPPLIER_ID"),false);r.put("supplierName",supplier.get("NAME"));r.put("supplierCode",supplier.get("SUPPLIER_CODE"));r.put("approvalId",l.get("ID"));r.put("supplierId",l.get("SUPPLIER_ID"));r.put("kind",l.get("BUSINESS_TYPE"));result.add(r);}}catch(vip.xiaonuo.common.exception.CommonException e){if(e.getCode()!=404)throw e;}
  }return result;
 }
 public List<Map<String,Object>> list(SrmActor a,String sid){suppliers.supplier(a,sid,false);return db.queryForList("SELECT ID AS id,BUSINESS_TYPE AS kind,STATUS AS status,REQUESTED_BY AS requestedBy,CREATED_AT AS createdAt,COMPLETED_AT AS completedAt FROM SRM_APPROVAL_LINK WHERE SUPPLIER_ID=? ORDER BY CREATED_AT DESC",sid);}
 public Map<String,Object> detail(SrmActor a,String aid){var l=link(aid,false);suppliers.supplier(a,str(l,"SUPPLIER_ID"),false);var r=new LinkedHashMap<String,Object>();for(String[] f:new String[][]{{"id","ID"},{"kind","BUSINESS_TYPE"},{"status","STATUS"},{"supplierId","SUPPLIER_ID"},{"requestedBy","REQUESTED_BY"},{"createdAt","CREATED_AT"}})r.put(f[0],l.get(f[1]));var payload=JSONUtil.parseObj(clear(l.get("PAYLOAD_CIPHER")));r.put("supplierName",suppliers.supplier(a,str(l,"SUPPLIER_ID"),false).get("NAME"));r.put("reason",payload.getStr("reason"));r.put("changes",a.sensitive()?payload.get("changes"):Map.of("notice","敏感变更内容仅字段授权人员可见"));r.put("before",a.sensitive()?JSONUtil.parseObj(clear(l.get("SNAPSHOT_CIPHER"))):Map.of());r.put("tasks",flow.tasks(str(l,"PROCESS_INSTANCE_ID")));r.put("history",flow.history(str(l,"PROCESS_INSTANCE_ID")));r.put("actions",db.queryForList("SELECT ID AS id,TASK_ID AS taskId,ACTION AS action,ACTOR_ID AS actorId,TARGET_ID AS targetId,COMMENT_CIPHER AS comment,CREATED_AT AS time FROM SRM_APPROVAL_ACTION WHERE APPROVAL_ID=? ORDER BY CREATED_AT,ID",aid).stream().map(m->{m.put("comment",clear(m.get("comment")));return m;}).toList());return r;}
 @Transactional public Map<String,Object> decide(SrmActor a,String aid,String tid,Decision p){
  // Lock the aggregate before the link everywhere to prevent lock-order inversions.
  var first=link(aid,false);var s=suppliers.supplier(a,str(first,"SUPPLIER_ID"),true);var l=link(aid,true);require(str(l,"STATUS").equals("PENDING"),409,"申请已处理");
  var t=flow.task(tid);require(flow.tasks(str(l,"PROCESS_INSTANCE_ID")).stream().anyMatch(x->tid.equals(x.get("id"))),404,"任务不属于该申请");require(canTask(a,l,t),403,"无任务处理权限或不能审核本人申请");
  require(Set.of("CLAIM","APPROVE","REJECT","RETURN","TRANSFER","ADD_SIGN").contains(p.action()),400,"不支持的审批动作");
  boolean sub="ADD_SIGN".equals(t.get("node"));if(sub)require(p.action().equals("APPROVE")||p.action().equals("REJECT"),400,"加签任务仅支持同意或驳回");
  if(Set.of("TRANSFER","ADD_SIGN").contains(p.action())){require(p.targetId()!=null&&!p.targetId().equals(a.userId())&&!p.targetId().equals(l.get("REQUESTED_BY")),400,"请选择其他审批人");require(eligible(l,role(t)).contains(p.targetId()),403,"接收人不具备当前组织、角色或数据权限");}
  db.update("INSERT INTO SRM_APPROVAL_ACTION(ID,APPROVAL_ID,TASK_ID,ACTION,ACTOR_ID,TARGET_ID,COMMENT_CIPHER,CREATED_AT) VALUES(?,?,?,?,?,?,?,NOW(3))",id(),aid,tid,p.action(),a.userId(),p.targetId(),codec.encrypt(p.comment()));
  switch(p.action()){
   case "CLAIM" -> flow.claim(tid,a.userId());
   case "TRANSFER" -> {flow.transfer(tid,p.targetId());notifyTasks(l);}
   case "ADD_SIGN" -> {require(flow.tasks(str(l,"PROCESS_INSTANCE_ID")).stream().filter(x->!str(x,"parentTaskId").isBlank()).count()<10,400,"单申请最多10个未完成加签");flow.addSign(tid,p.targetId());notifyTasks(l);}
   case "APPROVE" -> {flow.complete(tid,Map.of("decisionActor",a.userId()));if(!sub&&str(l,"BUSINESS_TYPE").equals("ONBOARD")&&t.get("node").equals("sqe"))notifyTasks(l);}
   case "RETURN","REJECT" -> {require(!p.action().equals("RETURN")||(str(l,"BUSINESS_TYPE").equals("ONBOARD")&&t.get("node").equals("sqe")),400,"仅SQE准入节点可以退回补正");flow.stop(str(l,"PROCESS_INSTANCE_ID"),p.action());String state=str(s,"STATUS");if(str(l,"BUSINESS_TYPE").equals("ONBOARD")){int count=((Number)s.get("CORRECTION_COUNT")).intValue()+(p.action().equals("RETURN")?1:0);state=p.action().equals("RETURN")?(count>=correctionLimit&&eliminateAtLimit?"ELIMINATED":"CORRECTION"):"POTENTIAL";db.update("UPDATE SRM_SUPPLIER SET STATUS=?,CORRECTION_COUNT=?,ROW_VERSION=ROW_VERSION+1,UPDATE_USER=?,UPDATE_TIME=NOW() WHERE ID=?",state,count,a.userId(),s.get("ID"));}db.update("UPDATE SRM_APPROVAL_LINK SET STATUS=?,COMPLETED_AT=NOW(3),ROW_VERSION=ROW_VERSION+1 WHERE ID=?",p.action().equals("RETURN")?"RETURNED":"REJECTED",aid);notifyResult(l,p.action());}
  }
  suppliers.audit(a,str(l,"SUPPLIER_ID"),aid,"APPROVAL_"+p.action(),Map.of("taskId",tid,"kind",l.get("BUSINESS_TYPE")));return detail(a,aid);
 }
 @Transactional public Map<String,Object> withdraw(SrmActor a,String aid,String comment){var first=link(aid,false);var s=suppliers.supplier(a,str(first,"SUPPLIER_ID"),true);var l=link(aid,true);require(a.superUser()||a.userId().equals(l.get("REQUESTED_BY")),403,"仅申请人或系统超管可撤回");require(str(l,"STATUS").equals("PENDING"),409,"已结束申请不能撤回");flow.stop(str(l,"PROCESS_INSTANCE_ID"),"WITHDRAWN");if(str(l,"BUSINESS_TYPE").equals("ONBOARD")){String previous=JSONUtil.parseObj(clear(l.get("SNAPSHOT_CIPHER"))).getStr("status");require(Set.of("POTENTIAL","CORRECTION").contains(previous),409,"申请状态快照异常");db.update("UPDATE SRM_SUPPLIER SET STATUS=?,ROW_VERSION=ROW_VERSION+1,UPDATE_TIME=NOW(),UPDATE_USER=? WHERE ID=?",previous,a.userId(),s.get("ID"));}db.update("UPDATE SRM_APPROVAL_LINK SET STATUS='WITHDRAWN',COMPLETED_AT=NOW(3),ROW_VERSION=ROW_VERSION+1 WHERE ID=?",aid);db.update("INSERT INTO SRM_APPROVAL_ACTION(ID,APPROVAL_ID,TASK_ID,ACTION,ACTOR_ID,COMMENT_CIPHER,CREATED_AT) VALUES(?,?,'PROCESS','WITHDRAW',?,?,NOW(3))",id(),aid,a.userId(),codec.encrypt(comment));suppliers.audit(a,str(s,"ID"),aid,"APPROVAL_WITHDRAWN",Map.of("kind",l.get("BUSINESS_TYPE")));notifyResult(l,"WITHDRAWN");return detail(a,aid);}
 @EventListener @Transactional public void completed(SrmFlowCompleted e){
  if(db.queryForObject("SELECT COUNT(*) FROM SRM_APPROVAL_LINK WHERE ID=?",Long.class,e.approvalId())==0)return;
  var first=link(e.approvalId(),false);var s=db.queryForMap("SELECT * FROM SRM_SUPPLIER WHERE ID=? FOR UPDATE",first.get("SUPPLIER_ID"));var l=link(e.approvalId(),true);
  require(str(l,"PROCESS_INSTANCE_ID").equals(e.processId())&&str(l,"STATUS").equals("PENDING"),409,"流程业务关联异常");require(ver(s)==((Number)l.get("BASE_VERSION")).longValue(),409,"档案在审批期间已变更，请撤回后重新申请");String state=str(s,"STATUS"),kind=str(l,"BUSINESS_TYPE");
  switch(kind){
   case "ONBOARD" -> {require(state.equals("REVIEWING"),409,"准入状态已变化");require(Boolean.TRUE.equals(suppliers.qualification(s).get("complete")),409,"审批回写失败：资质已过期或缺失，任务保留待重试");require(Set.of("QUALIFIED","PREFERRED","STRATEGIC","WATCH","TEMPORARY").contains(initialTier),500,"初始等级配置无效");state="QUALIFIED";db.update("UPDATE SRM_SUPPLIER SET ONBOARD_TIME=NOW(),TIER=? WHERE ID=?",initialTier,s.get("ID"));}
   case "FREEZE" -> {require(state.equals("QUALIFIED"),409,"冻结状态不匹配");state="FROZEN";}
   case "UNFREEZE" -> {require(state.equals("FROZEN"),409,"解冻状态不匹配");state="QUALIFIED";}
   case "ELIMINATE" -> {require(!state.equals("ELIMINATED"),409,"已淘汰");state="ELIMINATED";}
   case "CHANGE" -> {var json=JSONUtil.parseObj(clear(l.get("PAYLOAD_CIPHER")));var p=new com.fasterxml.jackson.databind.ObjectMapper().convertValue(json.getJSONObject("changes"),SrmInputs.SupplierEdit.class);validateChanges(s,p);String credit=p.creditCode()==null||p.creditCode().isBlank()?clear(s.get("CREDIT_CODE")):validCredit(p.creditCode());String bank=p.bankAccount()==null?clear(s.get("BANK_ACCOUNT")):p.bankAccount();db.update("UPDATE SRM_SUPPLIER SET NAME=?,CATEGORY=?,ADDRESS=?,BANK_NAME=?,CREDIT_CODE=?,CREDIT_CODE_FINGERPRINT=?,BANK_ACCOUNT=? WHERE ID=?",p.name(),p.category(),p.address()==null?str(s,"ADDRESS"):p.address(),p.bankName()==null?str(s,"BANK_NAME"):p.bankName(),codec.encrypt(credit),codec.creditFingerprint(str(s,"ORG_ID"),credit),bank.isBlank()?null:codec.encrypt(bank),s.get("ID"));}
   default -> throw new IllegalStateException("Unknown business type");
  }
  db.update("UPDATE SRM_SUPPLIER SET STATUS=?,ROW_VERSION=ROW_VERSION+1,UPDATE_TIME=NOW(),UPDATE_USER=? WHERE ID=?",state,e.actorId(),s.get("ID"));db.update("UPDATE SRM_APPROVAL_LINK SET STATUS='APPROVED',COMPLETED_AT=NOW(3),ROW_VERSION=ROW_VERSION+1 WHERE ID=?",l.get("ID"));
  var actor=new SrmActor(e.actorId(),str(s,"ORG_ID"),null,Set.of(),false,Set.of(str(s,"ORG_ID")),false,false,false);if(kind.equals("CHANGE"))suppliers.profileVersion(actor,s,db.queryForMap("SELECT * FROM SRM_SUPPLIER WHERE ID=?",s.get("ID")));suppliers.audit(actor,str(s,"ID"),e.approvalId(),"APPROVAL_APPLIED",Map.of("kind",kind,"state",state));notifyResult(l,"APPROVED");
 }
 public List<String> eligible(Map<String,Object> l,String role){var s=db.queryForMap("SELECT * FROM SRM_SUPPLIER WHERE ID=?",l.get("SUPPLIER_ID"));var result=new ArrayList<String>();String org=str(l,"ORG_ID");for(String uid:users.userIdsByOrgAndRole(org,role)){
   if(uid.equals(l.get("REQUESTED_BY")))continue;var ids=new ArrayList<String>();ids.add(uid);for(var r:authUsers.getRoleListByUserId(uid))ids.add(r.getStr("id"));for(var scope:authUsers.getPermissionListByUserIdAndRoleIdList(ids,org)){String path=scope.getStr("apiUrl");if(path==null||!new org.springframework.util.AntPathMatcher().match(path,"/srm/v1/approvals/tasks"))continue;var orgs=scope.getBeanList("dataScope",String.class);if(Boolean.TRUE.equals(scope.getBool("scopeAll"))||(orgs!=null&&orgs.contains(org))||((orgs==null||orgs.isEmpty())&&uid.equals(s.get("CREATE_USER")))){result.add(uid);break;}}
  }return result;
 }
 public List<Map<String,Object>> candidates(SrmActor a,String aid,String tid){var l=link(aid,false);suppliers.supplier(a,str(l,"SUPPLIER_ID"),false);var t=flow.task(tid);require(flow.tasks(str(l,"PROCESS_INSTANCE_ID")).stream().anyMatch(x->tid.equals(x.get("id"))),404,"任务不存在");require(canTask(a,l,t),403,"没有任务权限");return eligible(l,role(t)).stream().map(uid->{var u=users.getUserByIdWithoutException(uid);return Map.<String,Object>of("id",uid,"name",u==null?uid:u.getStr("name",uid));}).toList();}
 private void notifyTasks(Map<String,Object> l){for(var t:flow.tasks(str(l,"PROCESS_INSTANCE_ID"))){String assigned=str(t,"assignee");var recipients=assigned.isBlank()?eligible(l,role(t)):List.of(assigned);if(!recipients.isEmpty())messages.sendMessageWithContent(recipients,"SRM审批待办",str(l,"BUSINESS_TYPE")+"申请："+l.get("ID")+"；节点："+t.get("name"));outbox(l,"TASK_"+t.get("id"),"APPROVAL_TASK");}}
 private void notifyResult(Map<String,Object> l,String outcome){var targets=new LinkedHashSet<String>();targets.add(str(l,"REQUESTED_BY"));targets.addAll(db.queryForList("SELECT ACCOUNT_ID FROM SRM_SUPPLIER_CONTACT WHERE SUPPLIER_ID=? AND ENABLED=true AND ACCOUNT_ID IS NOT NULL AND DELETE_FLAG='NOT_DELETE' AND JSON_CONTAINS(ROLES,'\"ADMIN\"')",String.class,l.get("SUPPLIER_ID")));messages.sendMessageWithContent(new ArrayList<>(targets),"SRM审批结果","申请："+l.get("ID")+"；结果："+outcome);outbox(l,"RESULT_"+outcome,"APPROVAL_RESULT");}
 private void outbox(Map<String,Object> l,String suffix,String type){db.update("INSERT IGNORE INTO SRM_EVENT_OUTBOX(ID,ORG_ID,EVENT_KEY,BUSINESS_KEY,EVENT_TYPE,PAYLOAD,STATUS,ATTEMPTS,NEXT_RETRY_AT,CREATED_AT) VALUES(?,?,?,?,?,?,'PENDING',0,NOW(3),NOW(3))",id(),l.get("ORG_ID"),l.get("ID")+":"+suffix,l.get("ID"),type,JSONUtil.toJsonStr(Map.of("approvalId",l.get("ID"),"supplierId",l.get("SUPPLIER_ID"),"type",type)));}
 public List<Map<String,Object>> definitions(SrmActor a){require(!a.portal()&&(a.superUser()||a.roles().contains("SYS-ADMIN")),403,"仅流程管理员可管理定义");return flow.definitions();}
 public List<Map<String,Object>> messageStatus(SrmActor a){definitions(a);require(a.superUser(),403,"仅系统超管可查看跨组织消息监控");return db.queryForList("SELECT ID AS id,ORG_ID AS orgId,BUSINESS_KEY AS businessKey,EVENT_TYPE AS eventType,STATUS AS status,ATTEMPTS AS attempts,NEXT_RETRY_AT AS nextRetryAt,LAST_ERROR AS lastError FROM SRM_EVENT_OUTBOX ORDER BY CREATED_AT DESC LIMIT 200");}
 @Transactional public void retryMessage(SrmActor a,String id){messageStatus(a);require(db.update("UPDATE SRM_EVENT_OUTBOX SET STATUS='RETRY',ATTEMPTS=0,NEXT_RETRY_AT=NOW(3),LAST_ERROR=NULL WHERE ID=? AND STATUS='FAILED'",id)==1,409,"仅失败消息可重新投递");}
 @Transactional public String publish(SrmActor a,String kind){definitions(a);return flow.publish(kind);}
 @Transactional public String upload(SrmActor a,String kind,String xml){definitions(a);return flow.publishXml(kind,xml);}
 public String exportXml(SrmActor a,String id){definitions(a);return flow.exportXml(id);}
 @Transactional public void suspend(SrmActor a,String id,boolean value){definitions(a);flow.suspend(id,value);}
}
