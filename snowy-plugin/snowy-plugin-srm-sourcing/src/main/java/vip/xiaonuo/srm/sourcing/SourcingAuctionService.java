package vip.xiaonuo.srm.sourcing;
import java.util.*;
import java.math.*;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.*;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import static vip.xiaonuo.srm.sourcing.SourcingStore.*;
import static vip.xiaonuo.srm.sourcing.SourcingRules.*;
import static vip.xiaonuo.srm.sourcing.SourcingInputs.*;
@Service @RequiredArgsConstructor @Transactional(isolation=Isolation.READ_COMMITTED)
public class SourcingAuctionService {
 private final SourcingStore x;private final SourcingAuctionCache cache;
 public record Snapshot(String rfqId,long version,String checkpoint,long minCents,Map<String,Object> publicState,Map<String,Map<String,Object>> personal){}
 String checkpoint(Map<String,Object> r){return DigestUtil.sha256Hex(x.json(List.of(s(r,"ID"),n(r,"ROW_VERSION"),s(r,"STATUS"),n(r,"START_MS"),n(r,"DEADLINE_MS"),s(r,"LOWEST_CIPHER"),s(r,"WINNING_BID_ID"),s(r,"CONFIG_CIPHER"))));}
 long anchor(Map<String,Object> r){return s(r,"LOWEST_CIPHER").isBlank()?cents(x.decimal(x.config(r).get("startingPrice"))):x.amount(r,"LOWEST_CIPHER");}
 public Map<String,Object> bid(Context a,Bid p){x.bidder(a);x.access.requireSupplier(a,a.supplierId(),true);var r=x.rfq(a,p.rfqId(),true);require(s(r,"STRATEGY").equals("AUCTION"),400,"非竞价询价");String hash=DigestUtil.sha256Hex(x.json(p));var prior=x.db.queryForList("SELECT * FROM SRM_SRC_BID WHERE RFQ_ID=? AND SUPPLIER_ID=? AND REQUEST_KEY=?",p.rfqId(),a.supplierId(),p.requestKey());if(!prior.isEmpty()){require(hash.equals(prior.get(0).get("REQUEST_HASH")),409,"出价请求键内容冲突");return receipt(prior.get(0),true);}
  x.eligibility.requireEligibleForNewBusiness(a.supplierId(),s(r,"ORG_ID"));require(s(r,"STATUS").equals("PUBLISHED"),409,"场次已封盘或撤回");x.version(r,p.expectedVersion());var cfg=x.config(r);long amount=cents(p.amount()),step=cents(x.decimal(cfg.get("minDecrement"))),now=Math.max(x.now(),n(r,"LAST_TIME_MS"));int window=((Number)cfg.get("antiSnipeMinutes")).intValue();long end=bidEnd(now,n(r,"START_MS"),n(r,"DEADLINE_MS"),window,anchor(r),step,amount);
  cache.gate(p.rfqId(),n(r,"ROW_VERSION"),checkpoint(r),amount,step);
  String bid=id(),cipher=x.crypto.encrypt(p.amount().setScale(2).toPlainString());long version=n(r,"ROW_VERSION")+1;
  x.db.update("INSERT INTO SRM_SRC_BID VALUES(?,?,?,?,?,?,?,?,?,?)",bid,p.rfqId(),a.supplierId(),cipher,version,p.requestKey(),hash,now,end,a.userId());
  x.db.update("UPDATE SRM_SRC_RFQ SET LOWEST_CIPHER=?,WINNING_BID_ID=?,DEADLINE_MS=?,LAST_TIME_MS=?,ROW_VERSION=? WHERE ID=?",cipher,bid,end,now,version,p.rfqId());
  x.audit(a,r,a.supplierId(),bid,"BID_ACCEPTED",Map.of("version",version,"acceptedAt",now,"requestHash",hash,"amountCipherHash",DigestUtil.sha256Hex(cipher)));x.event(p.rfqId());return receipt(x.db.queryForMap("SELECT * FROM SRM_SRC_BID WHERE ID=?",bid),false);
 }
 private Map<String,Object> receipt(Map<String,Object> b,boolean replayed){return Map.of("id",b.get("ID"),"rfqId",b.get("RFQ_ID"),"requestKey",b.get("REQUEST_KEY"),"version",b.get("SEQUENCE_NO"),"amount",x.crypto.decrypt(s(b,"AMOUNT_CIPHER")),"acceptedAt",b.get("ACCEPTED_MS"),"deadline",b.get("END_MS"),"replayed",replayed);}
 public Map<String,Object> receipt(Context a,String rid,String key){x.bidder(a);x.rfq(a,rid,false);var rows=x.db.queryForList("SELECT * FROM SRM_SRC_BID WHERE RFQ_ID=? AND SUPPLIER_ID=? AND REQUEST_KEY=?",rid,a.supplierId(),key);require(rows.size()==1,404,"未找到本方已提交出价收据");return receipt(rows.get(0),true);}
 public List<Map<String,Object>> history(Context a,String rid){x.rfq(a,rid,false);var args=new ArrayList<Object>();args.add(rid);String w=a.portal()?" AND SUPPLIER_ID=?":"";if(a.portal())args.add(a.supplierId());return x.db.queryForList("SELECT * FROM SRM_SRC_BID WHERE RFQ_ID=?"+w+" ORDER BY SEQUENCE_NO DESC LIMIT 1000",args.toArray()).stream().map(r->receipt(r,true)).toList();}
 @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.READ_COMMITTED)
 public Snapshot snapshotTrusted(String rid){var r=x.trusted(rid,false);require(s(r,"STRATEGY").equals("AUCTION"),400,"非竞价询价");return snapshot(r);}
 Snapshot snapshot(Map<String,Object> r){String rid=s(r,"ID"),hash=checkpoint(r);long version=n(r,"ROW_VERSION"),min=anchor(r);var rows=x.db.queryForList("SELECT b.*,i.ALIAS FROM SRM_SRC_BID b JOIN SRM_SRC_INVITE i ON i.RFQ_ID=b.RFQ_ID AND i.SUPPLIER_ID=b.SUPPLIER_ID WHERE b.RFQ_ID=? AND b.SEQUENCE_NO=(SELECT MAX(t.SEQUENCE_NO) FROM SRM_SRC_BID t WHERE t.RFQ_ID=b.RFQ_ID AND t.SUPPLIER_ID=b.SUPPLIER_ID)",rid);rows.sort(Comparator.comparingLong(b->x.amount(b,"AMOUNT_CIPHER")));var board=new ArrayList<Map<String,Object>>();var own=new HashMap<String,Map<String,Object>>();int rank=0;
  for(var b:rows){rank++;BigDecimal amount=money(x.amount(b,"AMOUNT_CIPHER"));board.add(Map.of("alias",b.get("ALIAS"),"amount",amount,"rank",rank));own.put(s(b,"SUPPLIER_ID"),Map.of("yourPrice",amount,"yourRank",rank,"alias",b.get("ALIAS")));}
  var pub=new LinkedHashMap<String,Object>();pub.put("rfqId",rid);pub.put("version",version);pub.put("status",r.get("STATUS"));pub.put("serverTime",Math.max(x.now(),n(r,"LAST_TIME_MS")));pub.put("startAt",r.get("START_MS"));pub.put("deadline",r.get("DEADLINE_MS"));pub.put("lowestPrice",s(r,"LOWEST_CIPHER").isBlank()?null:money(min));pub.put("board",board);pub.put("cacheReady",cache.matches(rid,version,hash));pub.put("antiSnipeMinutes",x.config(r).get("antiSnipeMinutes"));pub.put("unlimitedExtensions",true);return new Snapshot(rid,version,hash,min,pub,own);
 }
 public Map<String,Object> state(Context a,String rid){var r=x.rfq(a,rid,false);require(s(r,"STRATEGY").equals("AUCTION"),400,"非竞价询价");var data=snapshot(r);return Map.of("public",data.publicState(),"personal",a.portal()?data.personal().getOrDefault(a.supplierId(),Map.of()):Map.of());}
 public Map<String,Object> recover(Context a,String rid){x.writer(a);var r=x.rfq(a,rid,true);require(s(r,"STRATEGY").equals("AUCTION")&&!s(r,"STATUS").equals("DRAFT"),409,"仅已发布竞价可恢复缓存");var snap=snapshot(r);x.auditRfq(a,r,"AUCTION_CACHE_REBUILT",Map.of("version",snap.version()));cache.restore(rid,snap.version(),snap.checkpoint(),snap.minCents());x.events.publishEvent(new SourcingStore.Changed(rid));return state(a,rid);}
 public void close(String rid){var r=x.trusted(rid,true);if(!s(r,"STATUS").equals("PUBLISHED")||Math.max(x.now(),n(r,"LAST_TIME_MS"))<n(r,"DEADLINE_MS"))return;long count=s(r,"STRATEGY").equals("AUCTION")?x.db.queryForObject("SELECT COUNT(*) FROM SRM_SRC_BID WHERE RFQ_ID=?",Long.class,rid):x.db.queryForObject("SELECT COUNT(*) FROM SRM_SRC_QUOTE WHERE RFQ_ID=?",Long.class,rid);String status=count==0?"FAILED":"CLOSED";x.db.update("UPDATE SRM_SRC_RFQ SET STATUS=?,ROW_VERSION=ROW_VERSION+1,LAST_TIME_MS=? WHERE ID=?",status,Math.max(x.now(),n(r,"LAST_TIME_MS")),rid);x.auditRfq(x.system(r),r,"SOURCING_CLOSED",Map.of("status",status));x.notifyAll(rid,"CLOSED",count==0?"询价已截止，未收到有效报价，已流标":"询价已截止，进入定点评审");x.event(rid);}
}
