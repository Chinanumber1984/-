package vip.xiaonuo.srm.performance;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceRulesTest {
 static List<BigDecimal> n(String... values){return Arrays.stream(values).map(BigDecimal::new).toList();}
 @Test void weightedCalculationAndThresholdBoundaries(){var weights=n("30","35","20","15");var limits=n("90","80","70","60");PerformanceRules.template(weights,limits);assertEquals(new BigDecimal("86.00"),PerformanceRules.total(n("80","90","85","90"),weights));assertEquals("STRATEGIC",PerformanceRules.grade(new BigDecimal("90.00"),limits));assertEquals("PREFERRED",PerformanceRules.grade(new BigDecimal("89.99"),limits));assertEquals("WATCH",PerformanceRules.grade(new BigDecimal("60.00"),limits));assertEquals("ELIMINATION_SUGGESTED",PerformanceRules.grade(new BigDecimal("59.99"),limits));}
 @Test void rejectsNonUniformTotalInvalidThresholdsAndInvalidScores(){assertThrows(CommonException.class,()->PerformanceRules.template(n("30","35","20","20"),n("90","80","70","60")));assertThrows(CommonException.class,()->PerformanceRules.template(n("30","35","20","15"),n("90","90","70","60")));for(var v:List.of(n("-1","80","80","80"),n("101","80","80","80"),n("80.001","80","80","80"),n("80")))assertThrows(CommonException.class,()->PerformanceRules.scores(v));assertThrows(CommonException.class,()->PerformanceRules.period("2026-13"));assertThrows(CommonException.class,()->PerformanceRules.period("1999-12"));assertThrows(CommonException.class,()->PerformanceRules.period("9999-12"));}
 @Test void portalRolesCannotMaintainAndShippingOnlyCannotRead(){for(String role:List.of("ADMIN","QUOTE","QUALITY","FINANCE","SHIP")){var a=new Context("contact","org","supplier",Set.of(role),false,Set.of(),false,true);assertThrows(CommonException.class,()->PerformanceService.writable(a));if(role.equals("SHIP"))assertThrows(CommonException.class,()->PerformanceService.readable(a));else assertDoesNotThrow(()->PerformanceService.readable(a));}assertThrows(CommonException.class,()->PerformanceService.writable(new Context("u","org",null,Set.of("BUYER"),false,Set.of("org"),false,false)));}
}
