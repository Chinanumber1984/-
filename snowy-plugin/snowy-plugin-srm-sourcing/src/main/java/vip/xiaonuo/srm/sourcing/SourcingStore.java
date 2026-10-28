package vip.xiaonuo.srm.sourcing;
import java.util.*;
import java.time.*;
import java.math.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import vip.xiaonuo.srm.api.*;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import vip.xiaonuo.srm.performance.api.SrmPerformanceApi;
import static vip.xiaonuo.srm.sourcing.SourcingRules.*;
@Component @RequiredArgsConstructor
public class SourcingStore {
 final JdbcTemplate db;final SrmSupplierAccessApi access;final SrmSupplierEligibilityApi eligibility;final SrmDataProtectionApi crypto;final SrmBusinessFileApi files;final SrmPerformanceApi performance;final ObjectMapper mapper;final ApplicationEventPublisher events;final Clock sourcingClock;
 @org.springframework.beans.factory.annotation.Autowired(required=false) vip.xiaonuo.srm.performance.api.SrmRiskApi qualityRisk;
 public record Changed(String rfqId){}
 static String id(){return IdUtil.getSnowflakeNextIdStr();}static String s(Map<String,Object> r,String k){return r.get(k)==null?"":String.valueOf(r.get(k));}static long n(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 long now(){return sourcingClock.millis();}
 String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid sourcing data");}}
 @SuppressWarnings("unchecked") Map<String,Object> object(String value){try{return mapper.readValue(value,LinkedHashMap.class);}catch(Exception e){throw new IllegalStateException("Invalid stored sourcing document");}}
 <T>T parse(String value,Class<T> type){try{return mapper.readValue(value,type);}catch(Exception e){throw new IllegalStateException("Invalid sourcing snapshot");}}
 String hash(Object value){try{return DigestUtil.sha256Hex(mapper.copy().configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true).writeValueAsString(value));}catch(Exception e){throw new IllegalArgumentException("Invalid sourcing basis");}}
 Map<String,Object> config(Map<String,Object> r){return object(crypto.decrypt(s(r,"CONFIG_CIPHER")));}
 @SuppressWarnings("unchecked") List<Map<String,Object>> lines(Map<String,Object> r){return (List<Map<String,Object>>)config(r).get("lines");}
 BigDecimal decimal(Object v){return new BigDecimal(String.valueOf(v));}
 long amount(Map<String,Object> r,String key){return cents(new BigDecimal(crypto.decrypt(s(r,key))));}
 void readable(Context a){require(!a.portal()||!Collections.disjoint(a.roles(),Set.of("ADMIN","QUOTE")),403,"仅门户管理员或报价员可访问寻源");}
 void writer(Context a){require(!a.portal()&&!Collections.disjoint(a.roles(),Set.of("SRC-SPE","CAT-MGR","superAdmin")),403,"仅寻源专员或采购经理可维护寻源");}
 void bidder(Context a){readable(a);require(a.portal(),403,"仅受邀供应商报价员可报价或出价");}
 boolean orgScope(Context a,Map<String,Object> r){return a.allOrganizations()||a.ownOnly()&&a.orgId().equals(s(r,"ORG_ID"))&&a.userId().equals(s(r,"CREATED_BY"))||!a.ownOnly()&&a.organizationIds().contains(s(r,"ORG_ID"));}
 Map<String,Object> rfq(Context a,String rid,boolean lock){readable(a);var rows=db.queryForList("SELECT * FROM SRM_SRC_RFQ WHERE ID=?"+(lock?" FOR UPDATE":""),rid);require(rows.size()==1,404,"询价不存在或无权访问");var r=rows.get(0);
  boolean allowed=a.portal()?a.orgId().equals(s(r,"ORG_ID"))&&!s(r,"STATUS").equals("DRAFT")&&db.queryForObject("SELECT COUNT(*) FROM SRM_SRC_INVITE i WHERE i.RFQ_ID=? AND i.SUPPLIER_ID=? AND EXISTS(SELECT 1 FROM SRM_SRC_NOTICE pn WHERE pn.RFQ_ID=i.RFQ_ID AND pn.SUPPLIER_ID=i.SUPPLIER_ID AND pn.KIND='INVITATION')",Long.class,rid,a.supplierId())==1:orgScope(a,r);require(allowed,404,"询价不存在或无权访问");return r;
 }
 Map<String,Object> trusted(String rid,boolean lock){return db.queryForMap("SELECT * FROM SRM_SRC_RFQ WHERE ID=?"+(lock?" FOR UPDATE":""),rid);}
 Context system(Map<String,Object> r){String org=s(r,"ORG_ID");return new Context("SYSTEM",org,null,Set.of("SRC-SPE"),false,Set.of(org),false,false);}
 void version(Map<String,Object> r,long v){require(n(r,"ROW_VERSION")==v,409,"询价版本已变化，请刷新后重试");}
 List<String> invited(String rid){return db.queryForList("SELECT SUPPLIER_ID FROM SRM_SRC_INVITE WHERE RFQ_ID=? ORDER BY SUPPLIER_ID",String.class,rid);}
 void qualified(Context a,String sid,String org,boolean lock){var supplier=access.requireSupplier(a,sid,lock);require(supplier.orgId().equals(org),404,"受邀供应商不属于本询价组织");eligibility.requireEligibleForNewBusiness(sid,org);}
 void audit(Context a,Map<String,Object> r,String sid,String subject,String action,Map<String,Object> meta){var data=new LinkedHashMap<>(meta);data.put("rfqId",r.get("ID"));access.recordEvent(a,sid,subject,action,data);}
 void auditRfq(Context a,Map<String,Object> r,String action,Map<String,Object> meta){for(String sid:invited(s(r,"ID")))audit(a,r,sid,s(r,"ID"),action,meta);}
 void event(String rid){long version=db.queryForObject("SELECT ROW_VERSION FROM SRM_SRC_RFQ WHERE ID=?",Long.class,rid);db.update("INSERT IGNORE INTO SRM_SRC_EVENT(ID,RFQ_ID,VERSION,CREATED_AT) VALUES(?,?,?,NOW(3))",id(),rid,version);events.publishEvent(new Changed(rid));}
 void notice(String rid,String sid,String kind,String text){db.update("INSERT INTO SRM_SRC_NOTICE VALUES(?,?,?,?,?,NOW(3))",id(),rid,sid,kind,text);}
 void notifyAll(String rid,String kind,String text){for(String sid:invited(rid))notice(rid,sid,kind,text);}
 Map<String,Object> risk(Context a,String sid){var supplier=access.requireSupplier(a,sid,false);var rows=db.queryForList("SELECT ID AS id,LEVEL AS level,EVIDENCE_REF AS evidenceRef,NOTE AS note,CREATED_AT AS assessedAt FROM SRM_SRC_RISK WHERE SUPPLIER_ID=? AND ORG_ID=? ORDER BY CREATED_AT DESC,ID DESC LIMIT 1",sid,supplier.orgId());var out=new LinkedHashMap<String,Object>();out.put("source","MANUAL");if(rows.isEmpty()){out.put("level","UNKNOWN");out.put("notice","尚无人工风险评估，不代表无风险");}else out.putAll(rows.get(0));if(qualityRisk!=null){var current=qualityRisk.summary(supplier.orgId(),sid);if(Boolean.TRUE.equals(current.get("hasEvents"))){String level=String.valueOf(current.get("level")),manual=String.valueOf(out.get("level"));if(manual.equals("HIGH"))level="HIGH";else if(manual.equals("MEDIUM")&&!Set.of("HIGH","UNKNOWN").contains(level))level="MEDIUM";out.put("level",level);out.put("source","INTERNAL_LEDGER_AND_MANUAL");out.put("internalBasis",current);}}return out;}
}
