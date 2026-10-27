package vip.xiaonuo.srm.performance;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import vip.xiaonuo.common.exception.CommonException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceMonthlyJobTest {
 @Test void monthlyTaskUsesPreviousShanghaiMonthAndTrustedSourceOrganization(){
  var db=mock(JdbcTemplate.class);var service=mock(PerformanceService.class);var job=new PerformanceMonthlyJob(service,db);ReflectionTestUtils.setField(job,"enabled",true);
  String month=YearMonth.now(ZoneId.of("Asia/Shanghai")).minusMonths(1).toString();
  when(db.queryForList(anyString(),eq(month))).thenReturn(List.of(Map.of("SUPPLIER_ID","s1","ORG_ID","org1"),Map.of("SUPPLIER_ID","s2","ORG_ID","org2")));
  doThrow(new CommonException(409,"待申诉复核")).when(service).calculate(any(),eq("s1"),eq(month));
  assertDoesNotThrow(job::run);verify(service).calculate(argThat(a->a.orgId().equals("org1")&&a.organizationIds().equals(Set.of("org1"))&&!a.allOrganizations()&&a.userId().equals("SYSTEM")),eq("s1"),eq(month));verify(service).calculate(argThat(a->a.orgId().equals("org2")&&!a.portal()),eq("s2"),eq(month));
 }
 @Test void disabledMonthlyJobDoesNotReadSources(){var db=mock(JdbcTemplate.class);var service=mock(PerformanceService.class);var job=new PerformanceMonthlyJob(service,db);job.run();verifyNoInteractions(db,service);}
}
