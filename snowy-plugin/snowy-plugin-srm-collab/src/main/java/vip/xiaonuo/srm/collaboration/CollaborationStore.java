package vip.xiaonuo.srm.collaboration;
import java.util.*;
import java.math.*;
import java.time.*;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import vip.xiaonuo.srm.api.*;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import vip.xiaonuo.srm.exchange.api.SrmExchangeApi;
import vip.xiaonuo.srm.collaboration.api.SrmCollaborationIngressApi.Origin;
import static vip.xiaonuo.srm.collaboration.CollaborationRules.*;
@Component @RequiredArgsConstructor
public class CollaborationStore {
 final JdbcTemplate db;final SrmSupplierAccessApi access;final SrmSupplierEligibilityApi eligibility;
 final SrmDataProtectionApi crypto;final SrmBusinessFileApi files;final SrmExchangeApi exchange;
 final ObjectMapper mapper;final Validator validator;
 static String id(){return IdUtil.getSnowflakeNextIdStr();}static String s(Map<String,Object> r,String key){return r.get(key)==null?"":String.valueOf(r.get(key));}static long n(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
 long now(){return System.currentTimeMillis();}LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
 String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("COLLABORATION_JSON_INVALID");}}
 Map<String,Object> object(String text){try{return mapper.readValue(text,LinkedHashMap.class);}catch(Exception e){throw new IllegalArgumentException("COLLABORATION_JSON_INVALID");}}
 String hash(Object value){try{return DigestUtil.sha256Hex(mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true).writeValueAsString(value));}catch(Exception e){throw new IllegalArgumentException("COLLABORATION_HASH_INVALID");}}
 <T>T input(Object data,Class<T> type){try{T p=mapper.convertValue(data,type);require(validator.validate(p).isEmpty(),400,"来源业务字段、金额精度或日期无效");return p;}catch(vip.xiaonuo.common.exception.CommonException e){throw e;}catch(Exception e){throw new vip.xiaonuo.common.exception.CommonException(400,"来源业务数据格式无效");}}
 BigDecimal decimal(Object value){return new BigDecimal(String.valueOf(value));}BigDecimal clearAmount(Map<String,Object> row,String key){return decimal(crypto.decrypt(s(row,key)));}
 boolean has(Context a,String... roles){return !Collections.disjoint(a.roles(),Set.of(roles));}
 void readable(Context a){require(a.portal()?has(a,"ADMIN","SHIP","FINANCE"):has(a,"BUYER","FIN","CAT-MGR","superAdmin"),403,"当前角色无采购协同权限");}
 void buyer(Context a){require(!a.portal()&&has(a,"BUYER","CAT-MGR","superAdmin"),403,"仅采购人员可操作");}
 void finance(Context a){require(a.portal()?has(a,"ADMIN","FINANCE"):has(a,"FIN","superAdmin"),403,"仅财务或本方门户管理员可操作");}
 void shipper(Context a){require(a.portal()&&has(a,"ADMIN","SHIP"),403,"仅本供应商发货员或管理员可操作");}
 boolean price(Context a){return a.portal()?has(a,"ADMIN","FINANCE","QUOTE"):access.canViewUnitPrice(a);}
 boolean scope(Context a,Map<String,Object> r){return a.portal()?a.orgId().equals(s(r,"ORG_ID"))&&a.supplierId().equals(s(r,"SUPPLIER_ID")):a.allOrganizations()||a.ownOnly()&&a.orgId().equals(s(r,"ORG_ID"))&&a.userId().equals(s(r,"CREATED_BY"))||!a.ownOnly()&&a.organizationIds().contains(s(r,"ORG_ID"));}
 String where(Context a,List<Object> args,String alias){readable(a);String p=alias.isBlank()?"":alias+".";if(a.portal()){args.add(a.orgId());args.add(a.supplierId());return p+"ORG_ID=? AND "+p+"SUPPLIER_ID=?";}if(a.allOrganizations())return "1=1";if(a.ownOnly()){args.add(a.orgId());args.add(a.userId());return p+"ORG_ID=? AND "+p+"CREATED_BY=?";}if(a.organizationIds().isEmpty())return "1=0";args.addAll(a.organizationIds());return p+"ORG_ID IN ("+String.join(",",Collections.nCopies(a.organizationIds().size(),"?"))+")";}
 Map<String,Object> aggregate(Context a,String table,String id,boolean lock){readable(a);var rows=db.queryForList("SELECT * FROM "+table+" WHERE ID=?"+(lock?" FOR UPDATE":""),id);require(rows.size()==1&&scope(a,rows.get(0)),404,"单据不存在或无权访问");return rows.get(0);}
 Map<String,Object> po(Context a,String id,boolean lock){return aggregate(a,"SRM_COL_PO",id,lock);}Map<String,Object> recon(Context a,String id,boolean lock){return aggregate(a,"SRM_COL_RECON",id,lock);}
 Map<String,Object> lockPo(Context a,String id){var r=po(a,id,false);access.requireSupplier(a,s(r,"SUPPLIER_ID"),true);return po(a,id,true);}
 Map<String,Object> lockRecon(Context a,String id){var r=recon(a,id,false);access.requireSupplier(a,s(r,"SUPPLIER_ID"),true);for(String po:db.queryForList("SELECT DISTINCT PO_ID FROM SRM_COL_RECON_LINE WHERE RECON_ID=? ORDER BY PO_ID",String.class,id))po(a,po,true);return recon(a,id,true);}
 void version(Map<String,Object> r,long expected){require(n(r,"ROW_VERSION")==expected,409,"单据版本已变化，请刷新");}
 Context system(Origin o){return new Context(o.operatorId(),o.orgId(),null,Set.of("BUYER","FIN","SYSTEM"),false,Set.of(o.orgId()),false,false);}
 void audit(Context a,String sid,String business,String action,Map<String,Object> data){access.recordEvent(a,sid,business,action,data);}
 Map<String,Object> policy(String org){var rows=db.queryForList("SELECT VERSION AS version,TOLERANCE_PCT AS tolerancePct FROM SRM_COL_POLICY WHERE ORG_ID=? ORDER BY VERSION DESC LIMIT 1",org);return rows.isEmpty()?Map.of("version",0L,"tolerancePct",new BigDecimal("3")):rows.get(0);}
 void notice(Map<String,Object> parent,String audience,String business,String kind,String message,String key){db.update("INSERT IGNORE INTO SRM_COL_NOTICE(ID,ORG_ID,SUPPLIER_ID,CREATED_BY,AUDIENCE,BUSINESS_ID,KIND,MESSAGE,EVENT_KEY,CREATED_AT) VALUES(?,?,?,?,?,?,?,?,?,NOW(3))",id(),parent.get("ORG_ID"),parent.get("SUPPLIER_ID"),parent.get("CREATED_BY"),audience,business,kind,message,key);}
 void notifyBoth(Map<String,Object> parent,String business,String kind,String message,String key){for(String audience:List.of("BUYER","PORTAL"))notice(parent,audience,business,kind,message,key);}
 void risk(Map<String,Object> po,String kind,String key,Map<String,Object> payload){db.update("INSERT INTO SRM_COL_RISK_EVENT(ID,ORG_ID,SUPPLIER_ID,PO_ID,KIND,EVENT_KEY,STATUS,PAYLOAD_CIPHER,CREATED_AT,UPDATED_AT) VALUES(?,?,?,?,?,?,'PENDING_ASSESSMENT',?,NOW(3),NOW(3)) ON DUPLICATE KEY UPDATE STATUS='PENDING_ASSESSMENT',PAYLOAD_CIPHER=VALUES(PAYLOAD_CIPHER),UPDATED_AT=NOW(3)",id(),po.get("ORG_ID"),po.get("SUPPLIER_ID"),po.get("ID"),kind,key,crypto.encrypt(json(payload)));}
 void refreshPo(String id){var p=db.queryForMap("SELECT * FROM SRM_COL_PO WHERE ID=?",id);String state;if(Boolean.TRUE.equals(p.get("CANCELLED")))state="CANCELLED";else if(db.queryForObject("SELECT COUNT(*) FROM SRM_COL_PO_CHANGE WHERE PO_ID=? AND STATUS='PENDING'",Long.class,id)>0)state="CHANGE_PENDING";else if(n(p,"CONFIRMED_VERSION")!=n(p,"SOURCE_VERSION"))state="WAIT_CONFIRM";else{BigDecimal ordered=db.queryForObject("SELECT COALESCE(SUM(QUANTITY),0) FROM SRM_COL_PO_LINE WHERE PO_ID=? AND ACTIVE=true",BigDecimal.class,id),received=db.queryForObject("SELECT COALESCE(SUM(l.QUANTITY),0) FROM SRM_COL_RECEIPT_LINE l JOIN SRM_COL_RECEIPT r ON r.ID=l.RECEIPT_ID WHERE r.PO_ID=? AND r.STATUS='POSTED'",BigDecimal.class,id);state=received.compareTo(ordered)>=0?"RECEIVED":db.queryForObject("SELECT COUNT(*) FROM SRM_COL_ASN WHERE PO_ID=? AND STATUS<>'WITHDRAWN'",Long.class,id)>0?"SHIPPING":"CONFIRMED";}db.update("UPDATE SRM_COL_PO SET STATUS=?,ROW_VERSION=ROW_VERSION+1,UPDATED_AT=NOW(3) WHERE ID=?",state,id);}
 Map<String,Object> basic(Map<String,Object> r,String[][] fields){var out=new LinkedHashMap<String,Object>();for(String[] pair:fields)out.put(pair[0],r.get(pair[1]));return out;}
}
