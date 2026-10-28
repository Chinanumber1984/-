package vip.xiaonuo.srm.quality;
import java.math.BigDecimal;import java.util.*;import static vip.xiaonuo.srm.quality.QualityStore.require;
public final class QualityRules {
 private QualityRules(){}
 public static BigDecimal qualityScore(List<BigDecimal> deductions){require(deductions!=null&&deductions.stream().allMatch(d->d!=null&&d.signum()>=0&&d.compareTo(new BigDecimal("100"))<=0),400,"单次质量扣分范围无效");return new BigDecimal("100").subtract(deductions.stream().reduce(BigDecimal.ZERO,BigDecimal::add)).max(BigDecimal.ZERO);}
 public static boolean responseOverdue(String status,long due,long now){return Set.of("OPEN","RETURNED","ESCALATED").contains(status)&&due<now;}
}
