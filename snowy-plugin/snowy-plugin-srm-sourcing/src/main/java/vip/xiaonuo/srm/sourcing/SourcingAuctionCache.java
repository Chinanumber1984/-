package vip.xiaonuo.srm.sourcing;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import static vip.xiaonuo.srm.sourcing.SourcingRules.*;
@Component @RequiredArgsConstructor
public class SourcingAuctionCache {
 private final StringRedisTemplate redis;private final SourcingStore x;
 private static final DefaultRedisScript<Long> GATE=new DefaultRedisScript<>("local raw=redis.call('GET',KEYS[1]); if not raw then return 0 end; local c=cjson.decode(raw); if tostring(c.version)~=ARGV[1] or c.checkpoint~=ARGV[2] then return 0 end; if tonumber(ARGV[3])>tonumber(c.minCents)-tonumber(ARGV[4]) then return -1 end; return 1",Long.class);
 private static final DefaultRedisScript<Long> SYNC=new DefaultRedisScript<>("local raw=redis.call('GET',KEYS[1]); if raw then local c=cjson.decode(raw); if tonumber(c.version)>tonumber(ARGV[1]) then return -1 end end; redis.call('SET',KEYS[1],ARGV[2]);return 1",Long.class);
 static String key(String rid){return "srm:sourcing:auction:"+rid;}
 public boolean matches(String rid,long version,String checkpoint){try{String raw=redis.opsForValue().get(key(rid));if(raw==null)return false;var c=x.object(raw);return String.valueOf(version).equals(String.valueOf(c.get("version")))&&checkpoint.equals(c.get("checkpoint"));}catch(Exception e){return false;}}
 public void gate(String rid,long version,String checkpoint,long amount,long step){try{Long result=redis.execute(GATE,List.of(key(rid)),String.valueOf(version),checkpoint,String.valueOf(amount),String.valueOf(step));require(result!=null&&result!=0,503,"竞价已暂停：缓存不可用或状态待恢复");require(result==1,409,"价格未满足当前最低降幅");}catch(vip.xiaonuo.common.exception.CommonException e){throw e;}catch(Exception e){throw new vip.xiaonuo.common.exception.CommonException(503,"竞价缓存不可用，新增出价已暂停");}}
 public void sync(String rid,long version,String checkpoint,long minCents){Long result=redis.execute(SYNC,List.of(key(rid)),String.valueOf(version),x.json(Map.of("version",String.valueOf(version),"checkpoint",checkpoint,"minCents",String.valueOf(minCents))));require(Long.valueOf(1).equals(result),503,"缓存版本异常，须授权恢复");}
 public void restore(String rid,long version,String checkpoint,long minCents){try{redis.opsForValue().set(key(rid),x.json(Map.of("version",String.valueOf(version),"checkpoint",checkpoint,"minCents",String.valueOf(minCents))));}catch(Exception e){throw new vip.xiaonuo.common.exception.CommonException(503,"缓存仍不可用，未恢复出价");}}
}
