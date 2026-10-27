package vip.xiaonuo.srm.performance.api;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
/** For authorized server-side sourcing callers only; trustedOrgId must come from the session. */
public interface SrmPerformanceApi {
 record Summary(String supplierId,String endPeriod,int requestedMonths,int validMonths,boolean insufficientHistory,boolean requiresApprovedException,List<String> missingPeriods,List<BigDecimal> averageScores,BigDecimal averageTotal,List<Map<String,Object>> trend) {}
 Summary summary(String supplierId,String trustedOrgId,YearMonth endPeriod,int months);
}
