package vip.xiaonuo.srm.exchange;

import java.util.*;
import java.util.function.Supplier;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.*;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.srm.api.*;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import vip.xiaonuo.srm.collaboration.api.SrmCollaborationIngressApi.Origin;

@Component @RequiredArgsConstructor
public class ExchangeStore {
 final JdbcTemplate db; final ObjectMapper mapper; final SrmDataProtectionApi crypto;
 final SrmSupplierAccessApi access; final PlatformTransactionManager transactionManager;
 @Value("${srm.integration.live-hosts:}") String liveHosts;
 static String id(){return IdUtil.getSnowflakeNextIdStr();}
 static String s(Map<String,Object> m,String k){return m.get(k)==null?"":String.valueOf(m.get(k));}
 static long n(Map<String,Object> m,String k){return ((Number)m.get(k)).longValue();}
 static void require(boolean v,int code,String msg){if(!v)throw new CommonException(code,msg);}
 <T>T tx(Supplier<T> work){return new TransactionTemplate(transactionManager).execute(status->work.get());}
 String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("EXCHANGE_JSON_INVALID");}}
 Map<String,Object> object(String json){try{return mapper.readValue(json,LinkedHashMap.class);}catch(Exception e){throw new IllegalArgumentException("EXCHANGE_JSON_INVALID");}}
 String hash(Object value){try{return DigestUtil.sha256Hex(mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true).writeValueAsString(value));}catch(Exception e){throw new IllegalArgumentException("EXCHANGE_JSON_INVALID");}}
 void admin(Context a){require(!a.portal()&&(a.roles().contains("SYS-ADMIN")||a.roles().contains("superAdmin")),403,"仅系统管理员可管理集成连接和人工重试");}
 boolean scope(Context a,String org){return !a.portal()&&(a.allOrganizations()||a.organizationIds().contains(org)||a.ownOnly()&&a.orgId().equals(org));}
 Map<String,Object> connector(String id){var rows=db.queryForList("SELECT * FROM SRM_XCH_CONNECTOR WHERE ID=?",id);require(rows.size()==1,404,"集成连接不存在");return rows.get(0);}
 Origin origin(Map<String,Object> c){return new Origin(s(c,"ID"),s(c,"ORG_ID"),s(c,"SYSTEM_KEY"),s(c,"MODE"),s(c,"CREATED_BY"));}
 java.net.URI endpoint(String system,String mode,String value){try{
  var u=java.net.URI.create(value);require(u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null,400,"集成地址不能包含凭证、查询或片段");
  if(mode.equals("SANDBOX")){String path=switch(system){case "ERP"->"/erp/events";case "MES"->"/mes/events";case "FINANCE"->"/finance/events";case "QMS"->"/qms/events";default->"";};require(u.getScheme().equals("http")&&"collab-sandbox".equals(u.getHost())&&u.getPort()==8090&&path.equals(u.getPath()),400,"沙箱只允许固定的本机私有服务");}
  else{Set<String> hosts=new HashSet<>(Arrays.asList(liveHosts.toLowerCase(Locale.ROOT).split(",")));require("https".equals(u.getScheme())&&u.getHost()!=null&&hosts.contains(u.getHost().toLowerCase(Locale.ROOT))&&(u.getPort()==-1||u.getPort()==443),400,"真实连接需HTTPS及服务端主机白名单");}
  return u;
 }catch(CommonException e){throw e;}catch(Exception e){throw new CommonException(400,"集成地址无效");}}
 static String canonical(String method,String path,String timestamp,String nonce,String body){return method+"\n"+path+"\n"+timestamp+"\n"+nonce+"\n"+DigestUtil.sha256Hex(body);}
 public static String signature(String secret,String method,String path,String timestamp,String nonce,String body){try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(canonical(method,path,timestamp,nonce,body).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("SIGNATURE_FAILURE");}}
 Map<String,Object> authenticate(String connectorId,String method,String path,String timestamp,String nonce,String signature,String body){
  var c=connector(connectorId);require(Boolean.TRUE.equals(c.get("ENABLED")),503,"集成连接已停用");
  long now=System.currentTimeMillis(),ts;try{ts=Long.parseLong(timestamp);}catch(Exception e){throw new CommonException(401,"请求签名时间无效");}
  require(ts>=now-300000&&ts<=now+300000&&nonce!=null&&nonce.matches("[A-Za-z0-9_-]{16,64}")&&signature!=null&&signature.matches("[0-9a-fA-F]{64}"),401,"请求签名格式或时间无效");
  String expected=signature(crypto.decrypt(s(c,"SECRET_CIPHER")),method,path,timestamp,nonce,body);
  require(MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)),401,"请求签名无效");
  try{db.update("INSERT INTO SRM_XCH_NONCE(CONNECTOR_ID,NONCE,EXPIRES_MS) VALUES(?,?,?)",connectorId,nonce,now+600000);}catch(org.springframework.dao.DuplicateKeyException e){throw new CommonException(409,"传输nonce已使用；业务重试请保留事件ID并换新nonce");}return c;
 }
}
