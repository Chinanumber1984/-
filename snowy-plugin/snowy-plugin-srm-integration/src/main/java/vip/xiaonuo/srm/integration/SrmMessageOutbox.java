package vip.xiaonuo.srm.integration;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import cn.hutool.json.JSONUtil;
/** At-least-once delivery. A timeout after remote acceptance may produce a duplicate message. */
@Component @RequiredArgsConstructor
public class SrmMessageOutbox {
 private final JdbcTemplate db;
 @Value("${srm.message.webhook-url:}")private String url;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
 public boolean configured(){return url!=null&&!url.isBlank();}
 static URI validated(String url){var u=URI.create(url);if(!"https".equals(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1||!Set.of("qyapi.weixin.qq.com","oapi.dingtalk.com").contains(u.getHost())||u.getFragment()!=null)throw new IllegalArgumentException("WEBHOOK_DESTINATION_INVALID");return u;}
 static boolean accepted(int status,String body){try{return status>=200&&status<300&&JSONUtil.parseObj(body).getInt("errcode",-1)==0;}catch(Exception ex){return false;}}
 @Scheduled(fixedDelayString="${srm.message.poll-ms:30000}") @Transactional public void dispatch(){
  if(!configured())return;URI target;try{target=validated(url);}catch(Exception ex){return;}
  for(var e:db.queryForList("SELECT * FROM SRM_EVENT_OUTBOX WHERE STATUS IN ('PENDING','RETRY') AND NEXT_RETRY_AT<=NOW(3) ORDER BY CREATED_AT LIMIT 20 FOR UPDATE SKIP LOCKED")){
   int attempts=((Number)e.get("ATTEMPTS")).intValue()+1;boolean ok=false;String reason="REMOTE_REJECTED";
   String text="SRM消息："+e.get("EVENT_TYPE")+"；申请："+e.get("BUSINESS_KEY")+"；事件："+e.get("EVENT_KEY");
   try{var request=HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSONUtil.toJsonStr(Map.of("msgtype","text","text",Map.of("content",text))))).build();var response=client.send(request,HttpResponse.BodyHandlers.ofString());ok=accepted(response.statusCode(),response.body());}catch(Exception ex){reason="TRANSPORT_FAILED";if(ex instanceof InterruptedException)Thread.currentThread().interrupt();}
   db.update("UPDATE SRM_EVENT_OUTBOX SET STATUS=?,ATTEMPTS=?,NEXT_RETRY_AT=DATE_ADD(NOW(3),INTERVAL ? SECOND),LAST_ERROR=? WHERE ID=?",ok?"SENT":attempts>=5?"FAILED":"RETRY",attempts,Math.min(3600,30*(1<<Math.min(attempts,6))),ok?null:reason,e.get("ID"));
  }
 }
}
