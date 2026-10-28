package vip.xiaonuo.srm.sourcing;
import java.util.*;
import java.math.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import static vip.xiaonuo.srm.sourcing.SourcingStore.*;
import static vip.xiaonuo.srm.sourcing.SourcingRules.*;
@Service @RequiredArgsConstructor
public class SourcingComparison {
 private final SourcingStore x;
 public Map<String,Object> compare(Context a,String rid){require(!a.portal(),403,"门户不能查看其他供应商比价");var r=x.rfq(a,rid,false);require(!s(r,"STATUS").equals("DRAFT"),409,"草稿尚无比价依据");return compare(a,r,YearMonth.now(ZoneId.of("Asia/Shanghai")).minusMonths(1));}
 @SuppressWarnings("unchecked") Map<String,Object> compare(Context a,Map<String,Object> r,YearMonth end){
  String rid=s(r,"ID");List<Map<String,Object>> offers;
  boolean auction=s(r,"STRATEGY").equals("AUCTION");if(auction)offers=x.db.queryForList("SELECT b.ID,b.SUPPLIER_ID,b.AMOUNT_CIPHER AS TOTAL_CIPHER,b.SEQUENCE_NO AS ROUND_NO FROM SRM_SRC_BID b WHERE b.RFQ_ID=? AND b.SEQUENCE_NO=(SELECT MAX(z.SEQUENCE_NO) FROM SRM_SRC_BID z WHERE z.RFQ_ID=b.RFQ_ID AND z.SUPPLIER_ID=b.SUPPLIER_ID)",rid);
  else offers=x.db.queryForList("SELECT q.ID,q.SUPPLIER_ID,q.TOTAL_CIPHER,q.PAYLOAD_CIPHER,q.ROUND_NO FROM SRM_SRC_QUOTE q WHERE q.RFQ_ID=? AND q.ROUND_NO=(SELECT MAX(z.ROUND_NO) FROM SRM_SRC_QUOTE z WHERE z.RFQ_ID=q.RFQ_ID AND z.SUPPLIER_ID=q.SUPPLIER_ID)",rid);
  var eligible=new ArrayList<Map<String,Object>>();var excluded=new ArrayList<Map<String,Object>>();
  for(var offer:offers){try{x.eligibility.requireEligibleForNewBusiness(s(offer,"SUPPLIER_ID"),s(r,"ORG_ID"));eligible.add(offer);}catch(vip.xiaonuo.common.exception.CommonException e){excluded.add(Map.of("supplierId",offer.get("SUPPLIER_ID"),"reason","当前不满足合格状态，不能定点"));}}
  var cfg=x.config(r);var weights=((List<Object>)cfg.get("weights")).stream().map(x::decimal).toList();BigDecimal minimum=eligible.stream().map(q->new BigDecimal(x.crypto.decrypt(s(q,"TOTAL_CIPHER")))).min(BigDecimal::compareTo).orElse(null);var candidates=new ArrayList<Map<String,Object>>();
  for(var offer:eligible){String sid=s(offer,"SUPPLIER_ID");var supplier=x.access.requireSupplier(a,sid,false);var performance=x.performance.summary(sid,s(r,"ORG_ID"),end,12);BigDecimal total=new BigDecimal(x.crypto.decrypt(s(offer,"TOTAL_CIPHER"))),cost=costScore(minimum,total);var c=new LinkedHashMap<String,Object>();c.put("supplierId",sid);c.put("supplierName",supplier.name());c.put("offerId",offer.get("ID"));c.put("round",offer.get("ROUND_NO"));c.put("total",total);if(!auction){var payload=x.object(x.crypto.decrypt(s(offer,"PAYLOAD_CIPHER")));var leadTimes=new ArrayList<Map<String,Object>>();for(var line:(List<Map<String,Object>>)payload.get("lines"))leadTimes.add(Map.of("lineId",line.get("lineId"),"leadDays",line.get("leadDays")));c.put("leadTimes",leadTimes);}c.put("costScore",cost);c.put("weightedScore",performance.requiresApprovedException()?null:weighted(cost,performance.averageScores(),weights));c.put("performance",performance);c.put("risk",x.risk(a,sid));candidates.add(c);}
  Comparator<Map<String,Object>> order=Comparator.<Map<String,Object>,Boolean>comparing(c->c.get("weightedScore")==null).thenComparing(c->c.get("weightedScore")==null?BigDecimal.ZERO:((BigDecimal)c.get("weightedScore")).negate()).thenComparing(c->(BigDecimal)c.get("total")).thenComparing(c->String.valueOf(c.get("supplierId")));if(auction)order=Comparator.<Map<String,Object>,BigDecimal>comparing(c->(BigDecimal)c.get("total")).thenComparing(c->String.valueOf(c.get("supplierId")));candidates.sort(order);int rank=0;for(var c:candidates)c.put("rank",++rank);
  return Map.of("rfqId",rid,"currency",r.get("CURRENCY"),"weights",weights,"weightVersion",cfg.get("weightVersion"),"formulaVersion","landed-ratio-v1","performanceEndPeriod",end.toString(),"candidates",candidates,"excluded",excluded,"proposedSupplierId",candidates.isEmpty()?"":candidates.get(0).get("supplierId"));
 }
 @SuppressWarnings("unchecked") Map<String,Object> basis(Context a,Map<String,Object> r,String sid,YearMonth end){var comparison=compare(a,r,end);var candidate=((List<Map<String,Object>>)comparison.get("candidates")).stream().filter(c->sid.equals(c.get("supplierId"))).findFirst();require(candidate.isPresent(),409,"供应商没有有效报价或当前不可定点");var b=new LinkedHashMap<String,Object>(candidate.get());b.put("rfqId",r.get("ID"));b.put("strategy",r.get("STRATEGY"));b.put("currency",r.get("CURRENCY"));b.put("weights",comparison.get("weights"));b.put("weightVersion",comparison.get("weightVersion"));b.put("formulaVersion",comparison.get("formulaVersion"));b.put("performanceEndPeriod",end.toString());b.put("configurationHash",cn.hutool.crypto.digest.DigestUtil.sha256Hex(s(r,"CONFIG_CIPHER")));return b;}
}
