package vip.xiaonuo.srm.sourcing;
import java.util.*;
import java.security.Principal;
import java.time.Duration;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import static vip.xiaonuo.srm.sourcing.SourcingRules.*;
import static vip.xiaonuo.srm.sourcing.SourcingStore.*;
@Component @RequiredArgsConstructor
public class SourcingSocketHub {
 private final SourcingStore x;private final SourcingAuctionService auctions;private final SimpMessagingTemplate messaging;private final StringRedisTemplate redis;
 private final java.util.concurrent.ExecutorService deliveries=java.util.concurrent.Executors.newFixedThreadPool(8,r->{var t=new Thread(r,"srm-sourcing-delivery");t.setDaemon(true);return t;});
 @jakarta.annotation.PreDestroy public void stop(){deliveries.shutdownNow();}
 private final ConcurrentHashMap<String,Connection> connections=new ConcurrentHashMap<>();
 private static final DefaultRedisScript<String> CONSUME=new DefaultRedisScript<>("local v=redis.call('GET',KEYS[1]);if v then redis.call('DEL',KEYS[1]) end;return v",String.class);
 public static final class Connection implements Principal {
  final String sessionId,rfqId,token;final boolean portal;final long expires;long window;int frames;
  Connection(String sid,String rid,String token,boolean portal,long now){this.sessionId=sid;this.rfqId=rid;this.token=token;this.portal=portal;expires=now+15*60000;}
  public String getName(){return sessionId;}
 }
 public Map<String,Object> ticket(Context a,String rid,String token){require(token!=null&&!token.isBlank(),401,"连接票据需要当前登录令牌");var r=x.rfq(a,rid,false);require(s(r,"STRATEGY").equals("AUCTION"),400,"仅竞价可建立实时连接");x.access.socketContext(token,a.portal());String ticket=UUID.randomUUID().toString().replace("-","");redis.opsForValue().set("srm:sourcing:ticket:"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(ticket),x.json(Map.of("token",token,"portal",a.portal(),"rfqId",rid)),Duration.ofSeconds(30));return Map.of("ticket",ticket,"expiresIn",30,"endpoint","/api/srm-sourcing-ws");}
 public Context actor(Connection c){require(x.now()<c.expires,401,"实时连接已到期，请重新连接");var a=x.access.socketContext(c.token,c.portal);x.rfq(a,c.rfqId,false);return a;}
 public Message<?> intercept(Message<?> message){var h=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);if(h==null||h.getCommand()==null)return message;var command=h.getCommand();
  if(command==StompCommand.CONNECT){String ticket=h.getFirstNativeHeader("ticket");require(ticket!=null&&ticket.matches("[0-9a-f]{32}"),401,"实时连接票据无效");String raw=redis.execute(CONSUME,List.of("srm:sourcing:ticket:"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(ticket)));require(raw!=null,401,"实时票据已使用或过期");var t=x.object(raw);var c=new Connection(h.getSessionId(),String.valueOf(t.get("rfqId")),String.valueOf(t.get("token")),Boolean.TRUE.equals(t.get("portal")),x.now());var a=actor(c);require(connections.size()<256,503,"实时连接数量达到上限");long owned=connections.values().stream().filter(z->z.portal==c.portal&&z.token.equals(c.token)&&z.rfqId.equals(c.rfqId)).count();require(owned<4,429,"当前账号场次连接过多");connections.put(c.sessionId,c);h.setUser(c);return message;}
  if(command==StompCommand.DISCONNECT||command==StompCommand.UNSUBSCRIBE)return message;
  require(h.getUser() instanceof Connection,401,"实时连接未认证");var c=(Connection)h.getUser();actor(c);
  synchronized(c){long bucket=x.now()/1000;if(c.window!=bucket){c.window=bucket;c.frames=0;}require(++c.frames<=20,429,"实时请求过于频繁");}
  if(command==StompCommand.SUBSCRIBE)require(Set.of("/user/queue/sourcing-state","/user/queue/sourcing-acks").contains(h.getDestination()),403,"只能订阅本连接私有队列");
  else if(command==StompCommand.SEND)require(Set.of("/app/bid/submit","/app/bid/snapshot").contains(h.getDestination()),403,"禁止发送到未知目的地或直接发布到消息队列");
  else require(command==StompCommand.ACK||command==StompCommand.NACK,403,"不支持的实时命令");return message;
 }
 public void snapshot(Connection c){var a=actor(c);var snap=auctions.snapshotTrusted(c.rfqId);send(c,snap,a);}
 private void send(Connection c,SourcingAuctionService.Snapshot s,Context a){messaging.convertAndSendToUser(c.getName(),"/queue/sourcing-state",Map.of("type","PUBLIC_STATE","state",s.publicState()));if(a.portal())messaging.convertAndSendToUser(c.getName(),"/queue/sourcing-state",Map.of("type","YOUR_STATE","rfqId",c.rfqId,"version",s.version(),"state",s.personal().getOrDefault(a.supplierId(),Map.of())));}
 public void broadcast(SourcingAuctionService.Snapshot snapshot){var pending=new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();for(var c:connections.values())if(c.rfqId.equals(snapshot.rfqId()))pending.add(java.util.concurrent.CompletableFuture.runAsync(()->{try{send(c,snapshot,actor(c));}catch(Exception ignored){connections.remove(c.sessionId);}},deliveries));java.util.concurrent.CompletableFuture.allOf(pending.toArray(java.util.concurrent.CompletableFuture[]::new)).join();}
 public void ack(Connection c,Object payload){messaging.convertAndSendToUser(c.getName(),"/queue/sourcing-acks",payload);}
 @EventListener public void disconnected(SessionDisconnectEvent event){connections.remove(event.getSessionId());}
}
