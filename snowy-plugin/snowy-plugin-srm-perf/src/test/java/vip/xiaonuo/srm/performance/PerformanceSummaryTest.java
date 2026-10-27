package vip.xiaonuo.srm.performance;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi;
import vip.xiaonuo.common.exception.CommonException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceSummaryTest {
 @Test void missingHistoryHasNullScoresAndRequiresApprovedException(){var db=mock(JdbcTemplate.class);when(db.queryForObject(anyString(),eq(Long.class),eq("s"),eq("org"))).thenReturn(1L);when(db.queryForObject(anyString(),eq(Long.class),eq("s"),eq("other"))).thenReturn(0L);when(db.queryForList(anyString(),eq("s"),eq("org"),anyString(),anyString())).thenReturn(List.of());var service=new PerformanceService(db,mock(SrmSupplierAccessApi.class),new ObjectMapper());var result=service.summary("s","org",YearMonth.of(2026,1),12);assertEquals(0,result.validMonths());assertTrue(result.requiresApprovedException());assertNull(result.averageTotal());assertTrue(result.averageScores().stream().allMatch(Objects::isNull));assertEquals(12,result.missingPeriods().size());assertThrows(CommonException.class,()->service.summary("s",null,YearMonth.of(2026,1),12));assertThrows(CommonException.class,()->service.summary("s","other",YearMonth.of(2026,1),12));}
 @Test void partialHistoryAveragesOnlyActualMonths(){var db=mock(JdbcTemplate.class);when(db.queryForObject(anyString(),eq(Long.class),eq("s"),eq("org"))).thenReturn(1L);when(db.queryForObject(anyString(),eq(Long.class),eq("s"),eq("other"))).thenReturn(0L);var r=new HashMap<String,Object>();r.put("ID","score");r.put("SUPPLIER_ID","s");r.put("PERIOD","2026-01");for(String k:List.of("DELIVER_SCORE","QUALITY_SCORE","COST_SCORE","SERVICE_SCORE","TOTAL"))r.put(k,new BigDecimal("75.00"));when(db.queryForList(anyString(),eq("s"),eq("org"),anyString(),anyString())).thenReturn(List.of(r));var result=new PerformanceService(db,mock(SrmSupplierAccessApi.class),new ObjectMapper()).summary("s","org",YearMonth.of(2026,1),12);assertEquals(new BigDecimal("75.00"),result.averageTotal());assertEquals(1,result.validMonths());assertEquals(11,result.missingPeriods().size());assertFalse(result.requiresApprovedException());assertTrue(result.insufficientHistory());assertEquals(1,result.trend().size());}
}
