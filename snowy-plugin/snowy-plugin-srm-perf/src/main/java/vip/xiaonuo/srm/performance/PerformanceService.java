package vip.xiaonuo.srm.performance;

import java.math.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import vip.xiaonuo.srm.performance.api.SrmPerformanceApi;
import static vip.xiaonuo.srm.performance.PerformanceRules.*;
import static vip.xiaonuo.srm.performance.PerformanceInputs.*;

@Service @RequiredArgsConstructor
public class PerformanceService implements SrmPerformanceApi {
 private final JdbcTemplate db;private final SrmSupplierAccessApi access;private final ObjectMapper mapper;
 static String id(){return IdUtil.getSnowflakeNextIdStr();}
 static String str(Map<String,Object> r,String k){return String.valueOf(r.get(k));}
 static long number(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 String json(Object v){try{return mapper.writeValueAsString(v);}catch(Exception ex){throw new CommonException(400,"绩效数据格式错误");}}
 @SuppressWarnings("unchecked") Map<String,Object> object(Object v){try{return mapper.readValue(String.valueOf(v),LinkedHashMap.class);}catch(Exception ex){throw new IllegalStateException("Invalid stored performance snapshot",ex);}}
 List<BigDecimal> decimals(Object v){try{return mapper.readValue(String.valueOf(v),mapper.getTypeFactory().constructCollectionType(List.class,BigDecimal.class));}catch(Exception ex){throw new IllegalStateException("Invalid stored score vector",ex);}}
 public static void readable(Context a){require(!a.portal()||!Collections.disjoint(a.roles(),Set.of("ADMIN","QUOTE","QUALITY","FINANCE")),403,"当前联系人角色不能查看绩效");}
 public static void writable(Context a){require(!a.portal()&&!Collections.disjoint(a.roles(),Set.of("SQE","CAT-MGR","superAdmin")),403,"仅SQE或采购经理可维护绩效");}
 public List<Map<String,Object>> templates(Context a){readable(a);return db.queryForList("SELECT ID AS version,EFFECTIVE_PERIOD AS effectivePeriod,CONFIG AS config,CREATED_AT AS createdAt FROM SRM_PERF_TEMPLATE ORDER BY ID DESC").stream().map(r->{r.put("config",object(r.get("config")));return r;}).toList();}
 @Transactional public Map<String,Object> publish(Context a,Template p){
  writable(a);templatePeriod(p.effectivePeriod());template(p.weights(),p.thresholds());
  db.queryForObject("SELECT ID FROM SRM_PERF_TEMPLATE WHERE ID=1 FOR UPDATE",Long.class);
  var latest=db.queryForMap("SELECT * FROM SRM_PERF_TEMPLATE ORDER BY ID DESC LIMIT 1 FOR UPDATE");
  require(number(latest,"ID")==p.expectedVersion(),409,"模板版本已变化，请刷新");
  require(p.effectivePeriod().compareTo(str(latest,"EFFECTIVE_PERIOD"))>=0,409,"新模板生效月份不能早于当前版本，历史模板不可覆盖");
  long version=p.expectedVersion()+1;db.update("INSERT INTO SRM_PERF_TEMPLATE VALUES(?,?,?,?,NOW(3))",version,p.effectivePeriod(),json(Map.of("weights",p.weights(),"thresholds",p.thresholds())),a.userId());
  return Map.of("version",version,"effectivePeriod",p.effectivePeriod());
 }
 private Map<String,Object> templateFor(String month){return db.queryForMap("SELECT * FROM SRM_PERF_TEMPLATE WHERE EFFECTIVE_PERIOD<=? ORDER BY EFFECTIVE_PERIOD DESC,ID DESC LIMIT 1",month);}
 private void noPending(String sid,String month){require(db.queryForObject("SELECT COUNT(*) FROM SRM_PERF_APPEAL a JOIN SRM_SCORECARD c ON c.ID=a.SCORE_ID WHERE c.SUPPLIER_ID=? AND c.PERIOD=? AND a.STATUS='PENDING'",Long.class,sid,month)==0,409,"评分有待复核申诉，不能覆盖复核依据");}
 @Transactional public Map<String,Object> recordTrustedSource(Context a,String sid,String month,List<BigDecimal> values,String mode,String reference){writable(a);period(month);scores(values);require(Set.of("SANDBOX","LIVE").contains(mode),400,"来源模式无效");access.requireSupplier(a,sid,true);noPending(sid,month);String iid=addInput(a,new Input(sid,month,mode.equals("LIVE")?"ERP_QMS":"SANDBOX_ERP_QMS",reference,values),null,mode.equals("LIVE")?"ERP_QMS":"SANDBOX_ERP_QMS");return Map.of("inputId",iid,"score",compute(a,sid,month));}
 private String addInput(Context a,Input p,String batch,String type){
  long revision=db.queryForObject("SELECT COALESCE(MAX(REVISION),0)+1 FROM SRM_PERF_INPUT WHERE SUPPLIER_ID=? AND PERIOD=?",Long.class,p.supplierId(),p.period());
  var s=access.requireSupplier(a,p.supplierId(),false);String input=id();
  db.update("INSERT INTO SRM_PERF_INPUT(ID,ORG_ID,SUPPLIER_ID,PERIOD,REVISION,SOURCE_TYPE,SOURCE_REF,SCORES,IMPORT_ID,CREATED_BY,CREATED_AT) VALUES(?,?,?,?,?,?,?,?,?,?,NOW(3))",input,s.orgId(),p.supplierId(),p.period(),revision,type,p.sourceRef(),json(p.scores()),batch,a.userId());
  access.recordEvent(a,p.supplierId(),input,"PERFORMANCE_INPUT_RECORDED",Map.of("period",p.period(),"sourceType",type,"revision",revision));return input;
 }
 @Transactional public Map<String,Object> importRows(Context a,Import p){
  writable(a);String hash=DigestUtil.sha256Hex(json(p.rows()));
  // Serialize each organization import, then lock supplier IDs in stable order.
  db.queryForList("SELECT ID FROM SYS_ORG WHERE ID=? FOR UPDATE",a.orgId());
  var old=db.queryForList("SELECT * FROM SRM_PERF_IMPORT WHERE ORG_ID=? AND REQUEST_KEY=?",a.orgId(),p.requestKey());
  if(!old.isEmpty()){require(hash.equals(old.get(0).get("CONTENT_HASH")),409,"请求键已用于不同导入内容");return Map.of("importId",old.get(0).get("ID"),"rows",old.get(0).get("ROW_COUNT"),"replayed",true);}
  Set<String> seen=new HashSet<>();for(var row:p.rows()){period(row.period());scores(row.scores());require(seen.add(row.supplierId()+":"+row.period()),400,"同批不能重复供应商月份");require(Set.of("HISTORICAL","SANDBOX").contains(row.sourceType()),400,"仅允许明确标记的历史或沙箱来源");}
  for(var sid:p.rows().stream().map(Input::supplierId).distinct().sorted().toList())access.requireSupplier(a,sid,true);
  String batch=id();db.update("INSERT INTO SRM_PERF_IMPORT VALUES(?,?,?,?,?,?,NOW(3))",batch,a.orgId(),p.requestKey(),hash,p.rows().size(),a.userId());
  for(var row:p.rows()){noPending(row.supplierId(),row.period());addInput(a,row,batch,row.sourceType());}
  return Map.of("importId",batch,"rows",p.rows().size(),"replayed",false);
 }
 @Transactional public Map<String,Object> calculate(Context a,String sid,String month){writable(a);period(month);access.requireSupplier(a,sid,true);noPending(sid,month);return compute(a,sid,month);}
 private Map<String,Object> compute(Context a,String sid,String month){
  var inputs=db.queryForList("SELECT * FROM SRM_PERF_INPUT WHERE SUPPLIER_ID=? AND PERIOD=? ORDER BY REVISION DESC LIMIT 1",sid,month);require(!inputs.isEmpty(),409,"本月无有效来源数据，不能生成虚构评分");
  var input=inputs.get(0);var template=templateFor(month);var config=object(template.get("CONFIG"));var weights=decimals(json(config.get("weights")));var thresholds=decimals(json(config.get("thresholds")));var values=decimals(input.get("SCORES"));
  var old=db.queryForList("SELECT * FROM SRM_SCORECARD WHERE SUPPLIER_ID=? AND PERIOD=? FOR UPDATE",sid,month);
  if(!old.isEmpty()&&old.get(0).get("INPUT_ID").equals(input.get("ID"))&&number(old.get(0),"TEMPLATE_ID")==number(template,"ID")){syncTier(a,sid,month,str(old.get(0),"GRADE"));return detail(a,str(old.get(0),"ID"));}
  BigDecimal total=total(values,weights);String grade=grade(total,thresholds),scoreId=old.isEmpty()?id():str(old.get(0),"ID");long revision=old.isEmpty()?1:number(old.get(0),"ROW_VERSION")+1;
  if(old.isEmpty())db.update("INSERT INTO SRM_SCORECARD VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3))",scoreId,input.get("ORG_ID"),sid,month,input.get("ID"),template.get("ID"),values.get(0),values.get(1),values.get(2),values.get(3),total,grade,revision);
  else db.update("UPDATE SRM_SCORECARD SET INPUT_ID=?,TEMPLATE_ID=?,DELIVER_SCORE=?,QUALITY_SCORE=?,COST_SCORE=?,SERVICE_SCORE=?,TOTAL=?,GRADE=?,ROW_VERSION=?,UPDATED_AT=NOW(3) WHERE ID=?",input.get("ID"),template.get("ID"),values.get(0),values.get(1),values.get(2),values.get(3),total,grade,revision,scoreId);
  var contributions=new ArrayList<BigDecimal>();for(int i=0;i<4;i++)contributions.add(values.get(i).multiply(weights.get(i)).divide(new BigDecimal("100"),4,RoundingMode.HALF_UP));
  var snapshot=new LinkedHashMap<String,Object>();snapshot.put("inputId",input.get("ID"));snapshot.put("inputRevision",input.get("REVISION"));snapshot.put("sourceType",input.get("SOURCE_TYPE"));snapshot.put("sourceRef",input.get("SOURCE_REF"));snapshot.put("scores",values);snapshot.put("weights",weights);snapshot.put("contributions",contributions);snapshot.put("thresholds",thresholds);snapshot.put("templateVersion",template.get("ID"));snapshot.put("period",month);snapshot.put("total",total);snapshot.put("grade",grade);snapshot.put("formulaVersion","weighted-v1");
  db.update("INSERT INTO SRM_PERF_SCORE_VERSION VALUES(?,?,?,?,?,NOW(3))",id(),scoreId,revision,json(snapshot),a.userId());
  syncTier(a,sid,month,grade);
  access.recordEvent(a,sid,scoreId,"PERFORMANCE_CALCULATED",Map.of("period",month,"revision",revision,"inputId",input.get("ID"),"templateVersion",template.get("ID"),"eliminationSuggested",grade.equals("ELIMINATION_SUGGESTED")));
  return detail(a,scoreId);
 }
 private void syncTier(Context a,String sid,String month,String grade){String source=db.queryForObject("SELECT i.SOURCE_TYPE FROM SRM_SCORECARD c JOIN SRM_PERF_INPUT i ON i.ID=c.INPUT_ID WHERE c.SUPPLIER_ID=? AND c.PERIOD=?",String.class,sid,month);if("SANDBOX_ERP_QMS".equals(source))return;if(month.equals(db.queryForObject("SELECT MAX(PERIOD) FROM SRM_SCORECARD WHERE SUPPLIER_ID=?",String.class,sid)))access.applyPerformanceTier(a,sid,grade.equals("ELIMINATION_SUGGESTED")?"WATCH":grade);}
 private Map<String,Object> rawScore(Context a,String cid,boolean lock){
  readable(a);var rows=db.queryForList("SELECT * FROM SRM_SCORECARD WHERE ID=?",cid);require(rows.size()==1,404,"评分不存在或无权访问");access.requireSupplier(a,str(rows.get(0),"SUPPLIER_ID"),lock);
  return lock?db.queryForMap("SELECT * FROM SRM_SCORECARD WHERE ID=? FOR UPDATE",cid):rows.get(0);
 }
 private Map<String,Object> view(Map<String,Object> c){
  var out=new LinkedHashMap<String,Object>();String[][] fields={{"id","ID"},{"supplierId","SUPPLIER_ID"},{"period","PERIOD"},{"deliverScore","DELIVER_SCORE"},{"qualityScore","QUALITY_SCORE"},{"costScore","COST_SCORE"},{"serviceScore","SERVICE_SCORE"},{"total","TOTAL"},{"grade","GRADE"},{"rowVersion","ROW_VERSION"},{"updatedAt","UPDATED_AT"}};
  for(var f:fields)out.put(f[0],c.get(f[1]));return out;
 }
 public Map<String,Object> detail(Context a,String cid){
  var c=rawScore(a,cid,false);var out=view(c);var s=access.requireSupplier(a,str(c,"SUPPLIER_ID"),false);out.put("supplierName",s.name());out.put("category",s.category());
  out.put("history",db.queryForList("SELECT REVISION AS revision,SNAPSHOT AS snapshot,CREATED_AT AS createdAt FROM SRM_PERF_SCORE_VERSION WHERE SCORE_ID=? ORDER BY REVISION DESC",cid).stream().map(r->{r.put("snapshot",object(r.get("snapshot")));return r;}).toList());
  out.put("appeals",db.queryForList("SELECT ID AS id,SCORE_VERSION AS scoreVersion,STATUS AS status,REASON AS reason,REVIEW_COMMENT AS reviewComment,CREATED_AT AS createdAt,REVIEWED_AT AS reviewedAt FROM SRM_PERF_APPEAL WHERE SCORE_ID=? ORDER BY CREATED_AT DESC",cid));return out;
 }
 private String scope(Context a,List<Object> args){
  if(a.portal()){args.add(a.orgId());args.add(a.supplierId());return "s.ORG_ID=? AND s.ID=?";}
  if(a.allOrganizations())return "1=1";
  if(a.ownOnly()){args.add(a.orgId());args.add(a.userId());return "s.ORG_ID=? AND s.CREATE_USER=?";}
  if(a.organizationIds().isEmpty())return "1=0";args.addAll(a.organizationIds());return "s.ORG_ID IN ("+String.join(",",Collections.nCopies(a.organizationIds().size(),"?"))+")";
 }
 public Map<String,Object> page(Context a,int page,int size,String month,String category,String grade){
  readable(a);require(page>=1&&page<=100000&&size>=1&&size<=100,400,"分页参数超出范围");var args=new ArrayList<Object>();String where="s.DELETE_FLAG='NOT_DELETE' AND "+scope(a,args);
  if(!month.isBlank()){period(month);where+=" AND c.PERIOD=?";args.add(month);}if(!category.isBlank()){where+=" AND s.CATEGORY=?";args.add(category);}if(!grade.isBlank()){require(Set.of("STRATEGIC","PREFERRED","QUALIFIED","WATCH","ELIMINATION_SUGGESTED").contains(grade),400,"不支持的绩效等级");where+=" AND c.GRADE=?";args.add(grade);}
  String from=" FROM SRM_SCORECARD c JOIN SRM_SUPPLIER s ON s.ID=c.SUPPLIER_ID AND s.ORG_ID=c.ORG_ID WHERE "+where;
  long count=db.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());args.add(size);args.add((page-1)*size);
  var rows=db.queryForList("SELECT c.*,s.NAME AS SUPPLIER_NAME,s.CATEGORY AS CATEGORY,(SELECT COUNT(*) FROM SRM_PERF_APPEAL a WHERE a.SCORE_ID=c.ID AND a.STATUS='PENDING') AS PENDING_APPEALS"+from+" ORDER BY c.PERIOD DESC,c.ID DESC LIMIT ? OFFSET ?",args.toArray());
  return Map.of("records",rows.stream().map(c->{var r=view(c);r.put("supplierName",c.get("SUPPLIER_NAME"));r.put("category",c.get("CATEGORY"));r.put("pendingAppeals",c.get("PENDING_APPEALS"));return r;}).toList(),"total",count,"current",page,"size",size);
 }
 @Transactional public Map<String,Object> appeal(Context a,String cid,Appeal p){
  readable(a);require(a.portal(),403,"仅供应商门户可发起绩效申诉");var c=rawScore(a,cid,true);require(number(c,"ROW_VERSION")==p.scoreVersion(),409,"评分已变化，请刷新后申诉");noPending(str(c,"SUPPLIER_ID"),str(c,"PERIOD"));String appeal=id();
  db.update("INSERT INTO SRM_PERF_APPEAL(ID,SCORE_ID,SCORE_VERSION,STATUS,REASON,CREATED_BY,CREATED_AT) VALUES(?,?,?,'PENDING',?,?,NOW(3))",appeal,cid,p.scoreVersion(),p.reason(),a.userId());
  access.recordEvent(a,str(c,"SUPPLIER_ID"),appeal,"PERFORMANCE_APPEALED",Map.of("scoreId",cid,"version",p.scoreVersion()));return Map.of("id",appeal);
 }
 @Transactional public Map<String,Object> review(Context a,String aid,Review p){
  require(!a.portal()&&(a.roles().contains("SQE")||a.roles().contains("superAdmin")),403,"仅SQE可复核申诉");
  var found=db.queryForList("SELECT SCORE_ID FROM SRM_PERF_APPEAL WHERE ID=?",aid);require(found.size()==1,404,"申诉不存在或无权访问");String cid=str(found.get(0),"SCORE_ID");var c=rawScore(a,cid,true);var appeal=db.queryForMap("SELECT * FROM SRM_PERF_APPEAL WHERE ID=? FOR UPDATE",aid);
  require("PENDING".equals(appeal.get("STATUS"))&&number(appeal,"SCORE_VERSION")==p.scoreVersion()&&number(c,"ROW_VERSION")==p.scoreVersion(),409,"申诉已处理或评分版本已变化");
  if(p.approve())scores(p.correctedScores());else require(p.correctedScores()==null,400,"驳回申诉不能同时修改分数");
  db.update("UPDATE SRM_PERF_APPEAL SET STATUS=?,REVIEW_COMMENT=?,REVIEWED_BY=?,REVIEWED_AT=NOW(3) WHERE ID=?",p.approve()?"APPROVED":"REJECTED",p.comment(),a.userId(),aid);
  if(p.approve()){addInput(a,new Input(str(c,"SUPPLIER_ID"),str(c,"PERIOD"),"HISTORICAL","appeal:"+aid+";input:"+c.get("INPUT_ID"),p.correctedScores()),null,"APPEAL_CORRECTION");compute(a,str(c,"SUPPLIER_ID"),str(c,"PERIOD"));}
  access.recordEvent(a,str(c,"SUPPLIER_ID"),aid,"PERFORMANCE_APPEAL_REVIEWED",Map.of("approved",p.approve(),"scoreId",cid));return detail(a,cid);
 }
 public Weights weightsFor(YearMonth month){var t=templateFor(month.toString());return new Weights(number(t,"ID"),decimals(json(object(t.get("CONFIG")).get("weights"))));}
 public Summary scopedSummary(Context a,String sid,String end,int months){readable(a);var s=access.requireSupplier(a,sid,false);return summary(sid,s.orgId(),period(end),months);}
 public Summary summary(String sid,String trustedOrgId,YearMonth end,int months){
  require(trustedOrgId!=null&&!trustedOrgId.isBlank(),403,"缺少可信组织");require(months>=1&&months<=36,400,"趋势月份须在1~36之间");period(end.toString());
  require(db.queryForObject("SELECT COUNT(*) FROM SRM_SUPPLIER WHERE ID=? AND ORG_ID=? AND DELETE_FLAG='NOT_DELETE'",Long.class,sid,trustedOrgId)==1,404,"供应商不存在或不属于授权组织");
  var rows=db.queryForList("SELECT * FROM SRM_SCORECARD WHERE SUPPLIER_ID=? AND ORG_ID=? AND PERIOD BETWEEN ? AND ? ORDER BY PERIOD",sid,trustedOrgId,end.minusMonths(months-1).toString(),end.toString());
  var missing=new ArrayList<String>();Set<String> present=new HashSet<>();rows.forEach(r->present.add(str(r,"PERIOD")));for(int i=months-1;i>=0;i--){String p=end.minusMonths(i).toString();if(!present.contains(p))missing.add(p);}
  if(rows.isEmpty())return new Summary(sid,end.toString(),months,0,true,true,missing,Collections.nCopies(4,null),null,List.of());
  var averages=new ArrayList<BigDecimal>();for(String key:List.of("DELIVER_SCORE","QUALITY_SCORE","COST_SCORE","SERVICE_SCORE","TOTAL")){BigDecimal sum=rows.stream().map(r->new BigDecimal(str(r,key))).reduce(BigDecimal.ZERO,BigDecimal::add);averages.add(sum.divide(BigDecimal.valueOf(rows.size()),2,RoundingMode.HALF_UP));}
  return new Summary(sid,end.toString(),months,rows.size(),rows.size()<months,false,missing,averages.subList(0,4),averages.get(4),rows.stream().map(this::view).toList());
 }
}
