package vip.xiaonuo.srm.workspace;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.common.util.CommonCryptogramUtil;
import vip.xiaonuo.client.ClientUserApi;
import vip.xiaonuo.srm.foundation.SensitiveFieldCodec;
import static vip.xiaonuo.srm.workspace.SrmInputs.*;

@Service @RequiredArgsConstructor
public class SrmWorkspaceService {
 private final JdbcTemplate db; private final SensitiveFieldCodec codec; private final ClientUserApi accounts;
 private void editable(String sid){require(db.queryForObject("SELECT COUNT(*) FROM SRM_APPROVAL_LINK WHERE SUPPLIER_ID=? AND STATUS='PENDING'",Long.class,sid)==0,409,"存在进行中的审批，不能修改审批依据");}
 public static final Set<String> CERT_TYPES=Set.of("BUSINESS_LICENSE","ISO9001","IATF16949","ENVIRONMENT","OTHER");
 public static final Set<String> CONTACT_ROLES=Set.of("ADMIN","QUOTE","SHIP","QUALITY","FINANCE");
 public static LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
 static String id(){return IdUtil.getSnowflakeNextIdStr();}
 static String str(Map<String,Object> m,String k){return m.get(k)==null?"":String.valueOf(m.get(k));}
 static long ver(Map<String,Object> m){return ((Number)m.get("ROW_VERSION")).longValue();}
 static String mask(String s){return s==null||s.isEmpty()?"":s.length()<8?"****":s.substring(0,3)+"****"+s.substring(s.length()-4);}
 static void require(boolean ok,int code,String message){if(!ok)throw new CommonException(code,message);}
 String where(SrmActor a,List<Object> values){
  if(a.portal()){values.add(a.orgId());values.add(a.supplierId());return "ORG_ID=? AND ID=?";}
  if(a.allOrganizations())return "1=1";
  if(a.ownOnly()){values.add(a.orgId());values.add(a.userId());return "ORG_ID=? AND CREATE_USER=?";}
  if(a.organizationIds().isEmpty())return "1=0";
  values.addAll(a.organizationIds());return "ORG_ID IN ("+String.join(",",Collections.nCopies(a.organizationIds().size(),"?"))+")";
 }
 public Map<String,Object> supplier(SrmActor a,String sid,boolean lock){
  var rows=db.queryForList("SELECT * FROM SRM_SUPPLIER WHERE ID=? AND DELETE_FLAG='NOT_DELETE'"+(lock?" FOR UPDATE":""),sid);
  require(rows.size()==1 && a.canRead(rows.get(0)),404,"供应商不存在或无权访问");return rows.get(0);
 }
 private Map<String,Object> summary(Map<String,Object> s,boolean sensitive){
  var v=new LinkedHashMap<String,Object>();
  for(String[] p:new String[][]{{"id","ID"},{"supplierCode","SUPPLIER_CODE"},{"name","NAME"},{"category","CATEGORY"},{"tier","TIER"},{"status","STATUS"},{"orgId","ORG_ID"},{"address","ADDRESS"},{"bankName","BANK_NAME"},{"rowVersion","ROW_VERSION"}})v.put(p[0],s.get(p[1]));
  String credit=str(s,"CREDIT_CODE"),bank=str(s,"BANK_ACCOUNT");
  v.put("creditCode",credit.isBlank()?"":sensitive?codec.decrypt(credit):mask(codec.decrypt(credit)));
  v.put("bankAccount",bank.isBlank()?"":sensitive?codec.decrypt(bank):mask(codec.decrypt(bank)));
  return v;
 }
 public Map<String,Object> page(SrmActor a,int current,int size,String search,String category,String status,String tier){return page(a,current,size,search,category,status,tier,"","createTime","DESC");}
 public Map<String,Object> page(SrmActor a,int current,int size,String search,String category,String status,String tier,String orgFilter,String sortBy,String sortOrder){
  var sortable=Map.of("createTime","CREATE_TIME","name","NAME","supplierCode","SUPPLIER_CODE","status","STATUS","tier","TIER");
  require(sortable.containsKey(sortBy)&&Set.of("ASC","DESC").contains(sortOrder),400,"不支持的排序字段或方向");
  require(current>=1&&current<=100000&&size>=1&&size<=100,400,"分页参数超出范围");var args=new ArrayList<Object>();
  String w="DELETE_FLAG='NOT_DELETE' AND "+where(a,args);
  if(orgFilter!=null&&!orgFilter.isBlank()){require(orgFilter.matches("[0-9]{1,20}"),400,"组织筛选格式不正确");w+=" AND ORG_ID=?";args.add(orgFilter);}
  if(search!=null&&!search.isBlank()){w+=" AND (NAME LIKE ? OR SUPPLIER_CODE LIKE ?)";args.add("%"+search+"%");args.add("%"+search+"%");}
  if(category!=null&&!category.isBlank()){w+=" AND CATEGORY=?";args.add(category);}
  if(status!=null&&!status.isBlank()){w+=" AND STATUS=?";args.add(status);}
  if(tier!=null&&!tier.isBlank()){w+=" AND TIER=?";args.add(tier);}
  var total=db.queryForObject("SELECT COUNT(*) FROM SRM_SUPPLIER WHERE "+w,Long.class,args.toArray());
  args.add(size);args.add((current-1)*size);
  var rows=db.queryForList("SELECT * FROM SRM_SUPPLIER WHERE "+w+" ORDER BY "+sortable.get(sortBy)+" "+sortOrder+",ID DESC LIMIT ? OFFSET ?",args.toArray());
  return Map.of("records",rows.stream().map(s->summary(s,false)).toList(),"total",total,"current",current,"size",size);
 }
 public Map<String,Object> detail(SrmActor a,String sid){
  var s=supplier(a,sid,false);var result=summary(s,a.sensitive());
  result.put("certificates",certificates(s));result.put("contacts",contacts(s));
  result.put("qualification",qualification(s));
  result.put("events",events(a,sid));
  return result;
 }
 public List<Map<String,Object>> categories(){return db.queryForList("SELECT CODE AS code,NAME AS name,REQUIRED_CERTS AS requiredCerts,ENABLED AS enabled,ROW_VERSION AS rowVersion FROM SRM_CATEGORY ORDER BY CODE");}
 private void categoryValid(String code){require(db.queryForObject("SELECT COUNT(*) FROM SRM_CATEGORY WHERE CODE=? AND ENABLED=true",Long.class,code)==1,400,"品类不存在或已停用");}
 public static String validCredit(String input){
  String s=input==null?"":input.trim().toUpperCase(Locale.ROOT);String alphabet="0123456789ABCDEFGHJKLMNPQRTUWXY";
  require(s.length()==18 && s.chars().allMatch(c->alphabet.indexOf(c)>=0),400,"统一社会信用代码格式不正确");
  int[] weights={1,3,9,27,19,26,16,17,20,29,25,13,8,24,10,30,28};int sum=0;
  for(int i=0;i<17;i++)sum+=alphabet.indexOf(s.charAt(i))*weights[i];
  require(s.charAt(17)==alphabet.charAt((31-sum%31)%31),400,"统一社会信用代码校验位不正确");return s;
 }
 @Transactional public Map<String,Object> create(SrmActor a,SupplierCreate p){
  require(!a.portal(),403,"门户不允许内部建档");a.requireManage();categoryValid(p.category());String credit=validCredit(p.creditCode()),sid=id();
  try {db.update("INSERT INTO SRM_SUPPLIER(ID,SUPPLIER_CODE,NAME,CATEGORY,TIER,STATUS,ORG_ID,ADDRESS,CREDIT_CODE,CREDIT_CODE_FINGERPRINT,BANK_ACCOUNT,BANK_NAME,DELETE_FLAG,CREATE_TIME,CREATE_USER,ROW_VERSION) VALUES(?,?,?,?,?,'POTENTIAL',?,?,?,?,?,?,'NOT_DELETE',NOW(),?,0)",sid,p.supplierCode()==null||p.supplierCode().isBlank()?"SUP"+sid:p.supplierCode(),p.name().trim(),p.category(),"TEMPORARY",a.orgId(),p.address(),codec.encrypt(credit),codec.creditFingerprint(a.orgId(),credit),p.bankAccount()==null||p.bankAccount().isBlank()?null:codec.encrypt(p.bankAccount()),p.bankName(),a.userId());}
  catch(DuplicateKeyException e){throw new CommonException(409,"供应商编码已存在，或当前组织已存在此信用代码对应的供应商");}
  audit(a,sid,sid,"SUPPLIER_CREATED",Map.of("fields",List.of("name","category","creditCode","bankAccount")));return detail(a,sid);
 }
 @Transactional public Map<String,Object> edit(SrmActor a,String sid,SupplierEdit p){
  a.requireManage();var s=supplier(a,sid,true);require(ver(s)==p.rowVersion(),409,"档案已变更，请刷新后重试");
  require(Set.of("POTENTIAL","CORRECTION","QUALIFIED","FROZEN").contains(str(s,"STATUS")),409,"当前状态不允许编辑档案");
  editable(sid);
  if(!p.category().equals(s.get("CATEGORY")))categoryValid(p.category());
  String credit=p.creditCode()==null||p.creditCode().isBlank()?codec.decrypt(str(s,"CREDIT_CODE")):validCredit(p.creditCode());
  String bank=p.bankAccount()==null?str(s,"BANK_ACCOUNT"):p.bankAccount().isBlank()?"":codec.encrypt(p.bankAccount());
  String bankName=p.bankName()==null?str(s,"BANK_NAME"):p.bankName(),address=p.address()==null?str(s,"ADDRESS"):p.address();
  boolean keyChanged=!p.name().trim().equals(str(s,"NAME"))||!p.category().equals(str(s,"CATEGORY"))||!bankName.equals(str(s,"BANK_NAME"))||!credit.equals(codec.decrypt(str(s,"CREDIT_CODE"))) || (p.bankAccount()!=null&&!p.bankAccount().equals(str(s,"BANK_ACCOUNT").isBlank()?"":codec.decrypt(str(s,"BANK_ACCOUNT"))));
  require(!keyChanged||Set.of("POTENTIAL","CORRECTION").contains(str(s,"STATUS")),409,"已准入供应商关键字段变更请提交变更审批");
  try{db.update("UPDATE SRM_SUPPLIER SET NAME=?,CATEGORY=?,ADDRESS=?,BANK_NAME=?,CREDIT_CODE=?,CREDIT_CODE_FINGERPRINT=?,BANK_ACCOUNT=?,UPDATE_TIME=NOW(),UPDATE_USER=?,ROW_VERSION=ROW_VERSION+1 WHERE ID=? AND ROW_VERSION=?",p.name().trim(),p.category(),address,bankName,codec.encrypt(credit),codec.creditFingerprint(str(s,"ORG_ID"),credit),bank.isBlank()?null:bank,a.userId(),sid,p.rowVersion());}
  catch(DuplicateKeyException e){throw new CommonException(409,"供应商编码已存在，或当前组织已存在此信用代码对应的供应商");}
  profileVersion(a,s,db.queryForMap("SELECT * FROM SRM_SUPPLIER WHERE ID=?",sid));
  audit(a,sid,sid,"SUPPLIER_UPDATED",Map.of("version",p.rowVersion()+1,"criticalChange",keyChanged));return detail(a,sid);
 }
 @Transactional public void tier(SrmActor a,String sid,TierInput p){
  require(a.canClassify(),403,"仅采购经理可维护等级");var s=supplier(a,sid,true);require(ver(s)==p.rowVersion(),409,"档案版本已变更");editable(sid);
  require(Set.of("STRATEGIC","PREFERRED","QUALIFIED","TEMPORARY","WATCH").contains(p.tier()),400,"不支持的供应商等级");
  db.update("UPDATE SRM_SUPPLIER SET TIER=?,ROW_VERSION=ROW_VERSION+1,UPDATE_TIME=NOW(),UPDATE_USER=? WHERE ID=?",p.tier(),a.userId(),sid);audit(a,sid,sid,"TIER_UPDATED",Map.of("tier",p.tier()));
 }
 @Transactional public void saveCategory(SrmActor a,CategoryInput p){
  require(a.canClassify(),403,"仅采购经理可维护品类");require(CERT_TYPES.containsAll(p.requiredCerts()),400,"存在未知资质类型");
  var rows=db.queryForList("SELECT * FROM SRM_CATEGORY WHERE CODE=? FOR UPDATE",p.code());
  if(rows.isEmpty())db.update("INSERT INTO SRM_CATEGORY(CODE,NAME,REQUIRED_CERTS,ENABLED) VALUES(?,?,?,?)",p.code(),p.name(),JSONUtil.toJsonStr(p.requiredCerts()),p.enabled());
  else{require(ver(rows.get(0))==p.rowVersion(),409,"品类已被修改");db.update("UPDATE SRM_CATEGORY SET NAME=?,REQUIRED_CERTS=?,ENABLED=?,ROW_VERSION=ROW_VERSION+1 WHERE CODE=?",p.name(),JSONUtil.toJsonStr(p.requiredCerts()),p.enabled(),p.code());}
 }
 public List<Map<String,Object>> certificates(Map<String,Object> s){
  var result=new ArrayList<Map<String,Object>>();
  for(var row:db.queryForList("SELECT * FROM SRM_SUPPLIER_CERT WHERE SUPPLIER_ID=? AND ORG_ID=? AND DELETE_FLAG='NOT_DELETE' ORDER BY CERT_TYPE,VERSION DESC",s.get("ID"),s.get("ORG_ID"))){
   var c=new LinkedHashMap<String,Object>();for(String[] f:new String[][]{{"id","ID"},{"certType","CERT_TYPE"},{"certNo","CERT_NO"},{"validFrom","VALID_FROM"},{"validTo","VALID_TO"},{"fileId","FILE_ID"},{"version","VERSION"},{"seriesId","SERIES_ID"}})c.put(f[0],row.get(f[1]));
   String status=str(row,"STATUS");if(!status.equals("VOID")){LocalDate start=LocalDate.parse(str(row,"VALID_FROM")),end=LocalDate.parse(str(row,"VALID_TO"));status=end.isBefore(today())?"EXPIRED":start.isAfter(today())?"PENDING":"VALID";}c.put("status",status);result.add(c);
  }return result;
 }
 public Map<String,Object> qualification(Map<String,Object> s){
  var rows=db.queryForList("SELECT REQUIRED_CERTS FROM SRM_CATEGORY WHERE CODE=?",s.get("CATEGORY"));
  List<String> required=rows.isEmpty()?List.of("BUSINESS_LICENSE"):JSONUtil.toList(str(rows.get(0),"REQUIRED_CERTS"),String.class);
  var valid=new HashSet<String>();for(var c:certificates(s))if(c.get("status").equals("VALID"))valid.add(String.valueOf(c.get("certType")));
  var missing=required.stream().filter(x->!valid.contains(x)).toList();return Map.of("complete",missing.isEmpty(),"missing",missing,"required",required);
 }
 @Transactional public void addCertificate(SrmActor a,String sid,CertificateInput p){
  a.requireManage();var s=supplier(a,sid,true);editable(sid);require(!str(s,"STATUS").equals("ELIMINATED"),409,"淘汰档案只读");require(CERT_TYPES.contains(p.certType()),400,"未知资质类型");require(!p.validTo().isBefore(p.validFrom()),400,"到期日期不能早于生效日期");
  require(db.queryForObject("SELECT COUNT(*) FROM SRM_ATTACHMENT_SCOPE WHERE FILE_ID=? AND SUPPLIER_ID=? AND ORG_ID=?",Long.class,p.fileId(),sid,s.get("ORG_ID"))==1,403,"附件不属于当前供应商");
  String series=id();int version=1;
  if(p.previousId()!=null&&!p.previousId().isBlank()){
   var old=db.queryForList("SELECT * FROM SRM_SUPPLIER_CERT WHERE ID=? AND SUPPLIER_ID=? AND ORG_ID=? AND DELETE_FLAG='NOT_DELETE' FOR UPDATE",p.previousId(),sid,s.get("ORG_ID"));
   require(old.size()==1&&!str(old.get(0),"STATUS").equals("VOID")&&p.certType().equals(old.get(0).get("CERT_TYPE")),409,"原证书不存在、已续期或类型不匹配");
   series=str(old.get(0),"SERIES_ID");version=((Number)old.get(0).get("VERSION")).intValue()+1;
   db.update("UPDATE SRM_SUPPLIER_CERT SET STATUS='VOID',UPDATE_TIME=NOW(),UPDATE_USER=? WHERE ID=?",a.userId(),p.previousId());
  }
  String cid=id();db.update("INSERT INTO SRM_SUPPLIER_CERT(ID,SUPPLIER_ID,ORG_ID,CERT_TYPE,CERT_NO,VALID_FROM,VALID_TO,FILE_ID,VERSION,SERIES_ID,STATUS,DELETE_FLAG,CREATE_TIME,CREATE_USER) VALUES(?,?,?,?,?,?,?,?,?,?,'VALID','NOT_DELETE',NOW(),?)",cid,sid,s.get("ORG_ID"),p.certType(),p.certNo(),p.validFrom(),p.validTo(),p.fileId(),version,series,a.userId());
  audit(a,sid,cid,"CERTIFICATE_SAVED",Map.of("version",version,"type",p.certType()));
 }
 public List<Map<String,Object>> contacts(Map<String,Object> s){
  var result=new ArrayList<Map<String,Object>>();
  for(var row:db.queryForList("SELECT * FROM SRM_SUPPLIER_CONTACT WHERE SUPPLIER_ID=? AND ORG_ID=? AND DELETE_FLAG='NOT_DELETE' ORDER BY CREATE_TIME,ID",s.get("ID"),s.get("ORG_ID"))){
   var c=new LinkedHashMap<String,Object>();c.put("id",row.get("ID"));c.put("name",row.get("NAME"));c.put("phone",mask(codec.decrypt(str(row,"PHONE"))));c.put("email",row.get("EMAIL"));c.put("roles",JSONUtil.toList(str(row,"ROLES"),String.class));c.put("enabled",row.get("ENABLED"));c.put("rowVersion",row.get("ROW_VERSION"));c.put("accountBound",row.get("ACCOUNT_ID")!=null);result.add(c);
  }return result;
 }
 private void roleCheck(Set<String> roles){require(roles!=null&&!roles.isEmpty()&&CONTACT_ROLES.containsAll(roles),400,"联系人角色不正确");}
 @Transactional public void addContact(SrmActor a,String sid,ContactInput p){
  a.requireManage();var s=supplier(a,sid,true);roleCheck(p.roles());
  boolean first=db.queryForObject("SELECT COUNT(*) FROM SRM_SUPPLIER_CONTACT WHERE SUPPLIER_ID=? AND DELETE_FLAG='NOT_DELETE'",Long.class,sid)==0;
  require(!first||(p.enabled()&&p.roles().contains("ADMIN")),409,"第一位联系人必须是启用的管理员");String cid=id();
  db.update("INSERT INTO SRM_SUPPLIER_CONTACT(ID,SUPPLIER_ID,ORG_ID,NAME,PHONE,EMAIL,ROLES,ENABLED,ROW_VERSION,DELETE_FLAG,CREATE_TIME,CREATE_USER) VALUES(?,?,?,?,?,?,?,?,0,'NOT_DELETE',NOW(),?)",cid,sid,s.get("ORG_ID"),p.name(),codec.encrypt(p.phone()),p.email(),JSONUtil.toJsonStr(p.roles()),p.enabled(),a.userId());audit(a,sid,cid,"CONTACT_CREATED",Map.of("roles",p.roles()));
 }
 @Transactional public void editContact(SrmActor a,String sid,String cid,ContactInput p){
  a.requireManage();var s=supplier(a,sid,true);roleCheck(p.roles());
  var list=db.queryForList("SELECT * FROM SRM_SUPPLIER_CONTACT WHERE ID=? AND SUPPLIER_ID=? AND ORG_ID=? AND DELETE_FLAG='NOT_DELETE' FOR UPDATE",cid,sid,s.get("ORG_ID"));require(list.size()==1,404,"联系人不存在");var old=list.get(0);require(ver(old)==p.rowVersion(),409,"联系人已被修改");
  if(!p.enabled()||!p.roles().contains("ADMIN")){
   long admins=db.queryForObject("SELECT COUNT(*) FROM SRM_SUPPLIER_CONTACT WHERE SUPPLIER_ID=? AND ID<>? AND ENABLED=true AND DELETE_FLAG='NOT_DELETE' AND JSON_CONTAINS(ROLES,'\"ADMIN\"')",Long.class,sid,cid);
   require(admins>0,409,"至少保留一位启用的供应商管理员");
  }
  db.update("UPDATE SRM_SUPPLIER_CONTACT SET NAME=?,PHONE=?,EMAIL=?,ROLES=?,ENABLED=?,ROW_VERSION=ROW_VERSION+1,UPDATE_TIME=NOW(),UPDATE_USER=? WHERE ID=?",p.name(),codec.encrypt(p.phone()),p.email(),JSONUtil.toJsonStr(p.roles()),p.enabled(),a.userId(),cid);
  if(old.get("ACCOUNT_ID")!=null)accounts.setPortalAccountEnabled(str(old,"ACCOUNT_ID"),p.enabled());
  audit(a,sid,cid,"CONTACT_UPDATED",Map.of("roles",p.roles(),"enabled",p.enabled()));
 }
 @Transactional public void contactAccess(SrmActor a,String sid,String cid,ContactAccess p){
  a.requireManage();var supplier=supplier(a,sid,true);var list=db.queryForList("SELECT * FROM SRM_SUPPLIER_CONTACT WHERE ID=? AND SUPPLIER_ID=? AND ORG_ID=? AND DELETE_FLAG='NOT_DELETE'",cid,sid,supplier.get("ORG_ID"));
  require(list.size()==1,404,"联系人不存在");var c=list.get(0);
  editContact(a,sid,cid,new ContactInput(str(c,"NAME"),codec.decrypt(str(c,"PHONE")),str(c,"EMAIL"),p.roles(),p.enabled(),p.rowVersion()));
 }
 @Transactional public void bindAccount(SrmActor a,String sid,String cid,AccountInput p){
  a.requireManage();require(!a.portal(),403,"门户账户开通由内部管理员执行");var s=supplier(a,sid,true);
  var rows=db.queryForList("SELECT * FROM SRM_SUPPLIER_CONTACT WHERE ID=? AND SUPPLIER_ID=? AND ORG_ID=? AND DELETE_FLAG='NOT_DELETE' FOR UPDATE",cid,sid,s.get("ORG_ID"));require(rows.size()==1,404,"联系人不存在");var c=rows.get(0);
  require(c.get("ACCOUNT_ID")==null&&Boolean.TRUE.equals(c.get("ENABLED")),409,"联系人已绑定账户或已停用");
  String password;try{password=CommonCryptogramUtil.doSm2Decrypt(p.passwordCipher());}catch(Exception ex){throw new CommonException(400,"密码传输格式不正确");}
  require(password.length()>=12 && password.matches("(?s).*[A-Za-z].*")&&password.matches("(?s).*[0-9].*"),400,"初始密码至少12位，包含字母和数字");
  try{db.update("INSERT INTO SRM_PORTAL_ACCOUNT_RESERVATION(CONTACT_ID,ACCOUNT) VALUES(?,?)",cid,p.account());String accountId=accounts.createPortalAccount(p.account(),password,str(c,"NAME"));db.update("UPDATE SRM_SUPPLIER_CONTACT SET ACCOUNT_ID=?,ROW_VERSION=ROW_VERSION+1 WHERE ID=?",accountId,cid);}
  catch(DuplicateKeyException ex){throw new CommonException(409,"账号已存在或联系人已开通");}
  catch(CommonException ex){throw new CommonException(400,ex.getMsg());}
  audit(a,sid,cid,"PORTAL_ACCOUNT_BOUND",Map.of("contactId",cid));
 }
 public void profileVersion(SrmActor a,Map<String,Object> before,Map<String,Object> after){db.update("INSERT INTO SRM_PROFILE_VERSION(ID,SUPPLIER_ID,ORG_ID,VERSION,BEFORE_CIPHER,AFTER_CIPHER,ACTOR_ID,CREATED_AT) VALUES(?,?,?,?,?,?,?,NOW(3))",id(),before.get("ID"),before.get("ORG_ID"),ver(after),codec.encrypt(JSONUtil.toJsonStr(summary(before,true))),codec.encrypt(JSONUtil.toJsonStr(summary(after,true))),a.userId());}
 public List<Map<String,Object>> versions(SrmActor a,String sid){supplier(a,sid,false);return db.queryForList("SELECT * FROM SRM_PROFILE_VERSION WHERE SUPPLIER_ID=? ORDER BY VERSION DESC LIMIT 100",sid).stream().map(r->{var out=new LinkedHashMap<String,Object>();out.put("version",r.get("VERSION"));out.put("actorId",r.get("ACTOR_ID"));out.put("time",r.get("CREATED_AT"));out.put("before",a.sensitive()?JSONUtil.parseObj(codec.decrypt(str(r,"BEFORE_CIPHER"))):Map.of("notice","无敏感字段权限"));out.put("after",a.sensitive()?JSONUtil.parseObj(codec.decrypt(str(r,"AFTER_CIPHER"))):Map.of("notice","无敏感字段权限"));return (Map<String,Object>)out;}).toList();}
 public List<Map<String,Object>> events(SrmActor a,String sid){
  supplier(a,sid,false);var result=new ArrayList<Map<String,Object>>();
  for(var e:db.queryForList("SELECT * FROM SRM_BUSINESS_EVENT WHERE SUPPLIER_ID=? ORDER BY CREATED_AT DESC,ID DESC LIMIT 100",sid)){
   boolean verified=verifyEvent(e);
   result.add(Map.of("id",e.get("ID"),"action",e.get("ACTION"),"actorId",e.get("ACTOR_ID"),"time",String.valueOf(e.get("CREATED_AT")),"signatureValid",verified));
  }return result;
 }
 private boolean verifyEvent(Map<String,Object> e){
  try{
   String doc=str(e,"EVENT_DOCUMENT");if(!CommonCryptogramUtil.doVerifySignature(doc,str(e,"SIGNATURE")))return false;
   var json=JSONUtil.parseObj(doc);
   for(String[] f:new String[][]{{"ID","id"},{"ORG_ID","orgId"},{"SUPPLIER_ID","supplierId"},{"SUBJECT_ID","subjectId"},{"ACTOR_ID","actorId"},{"ACTION","action"},{"TRACE_ID","traceId"}})if(!str(e,f[0]).equals(json.getStr(f[1])))return false;
   if(json.getInt("eventSchemaVersion",1)<2)return false;
   if(!LocalDateTime.parse(str(e,"CREATED_AT").replace(' ','T')).equals(LocalDateTime.parse(json.getStr("createdAt").replace(' ','T'))))return false;
   var mapper=new com.fasterxml.jackson.databind.ObjectMapper();return mapper.readTree(str(e,"METADATA")).equals(mapper.readTree(JSONUtil.toJsonStr(json.get("metadata"))));
  }catch(Exception ex){return false;}
 }
 public void audit(SrmActor a,String sid,String subject,String action,Map<String,Object> metadata){
  var eventTime=java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));String eid=id(),trace=id();String recordOrg=db.queryForObject("SELECT ORG_ID FROM SRM_SUPPLIER WHERE ID=?",String.class,sid);var d=new LinkedHashMap<String,Object>();d.put("id",eid);d.put("orgId",recordOrg);d.put("supplierId",sid);d.put("subjectId",subject);d.put("actorId",a.userId());d.put("action",action);d.put("traceId",trace);d.put("metadata",metadata);d.put("createdAt",eventTime.toString());d.put("eventSchemaVersion",2);String document=JSONUtil.toJsonStr(d);
  db.update("INSERT INTO SRM_BUSINESS_EVENT(ID,ORG_ID,SUPPLIER_ID,SUBJECT_ID,ACTOR_ID,ACTION,TRACE_ID,METADATA,CREATED_AT,EVENT_DOCUMENT,SIGNATURE) VALUES(?,?,?,?,?,?,?,?,?,?,?)",eid,recordOrg,sid,subject,a.userId(),action,trace,JSONUtil.toJsonStr(metadata),eventTime,document,CommonCryptogramUtil.doSignature(document));
 }
 public Map<String,Object> context(SrmActor a){return Map.of("userId",a.userId(),"orgId",a.orgId(),"supplierId",a.supplierId()==null?"":a.supplierId(),"roles",a.roles(),"portal",a.portal(),"canManage",a.canManage(),"canClassify",a.canClassify(),"sensitive",a.sensitive(),"scope",a.portal()?"SUPPLIER":a.allOrganizations()?"ALL":a.ownOnly()?"SELF":"ORGANIZATIONS","organizationIds",a.organizationIds());}
 public List<Map<String,Object>> alerts(SrmActor a){
  var args=new ArrayList<Object>();String w=where(a,args).replaceAll("\\bORG_ID\\b","s.ORG_ID").replaceAll("\\bID\\b","s.ID").replaceAll("\\bCREATE_USER\\b","s.CREATE_USER");
  return db.queryForList("SELECT a.ID AS id,a.SUPPLIER_ID AS supplierId,a.CERT_ID AS certId,a.VALID_TO AS validTo,a.KIND AS kind,a.STATUS AS status FROM SRM_CERT_ALERT a JOIN SRM_SUPPLIER s ON s.ID=a.SUPPLIER_ID WHERE s.DELETE_FLAG='NOT_DELETE' AND "+w+" ORDER BY a.CREATED_AT DESC LIMIT 100",args.toArray());
 }
}
