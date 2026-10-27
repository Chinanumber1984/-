package vip.xiaonuo.srm.workspace;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context;
import static org.mockito.Mockito.*;
class SrmPerformanceTierTest {
 @Test void activeLifecycleApprovalDefersTierChange(){var db=mock(JdbcTemplate.class);var suppliers=mock(SrmWorkspaceService.class);var service=new SrmSupplierAccess(mock(SrmIdentity.class),suppliers,db);var a=new Context("sqe","org",null,Set.of("SQE"),false,Set.of("org"),false,false);when(suppliers.supplier(any(),eq("s"),eq(true))).thenReturn(Map.of("ORG_ID","org","NAME","supplier","CATEGORY","MACHINING","STATUS","QUALIFIED","TIER","QUALIFIED"));when(db.queryForObject(anyString(),eq(Long.class),eq("s"))).thenReturn(1L);service.applyPerformanceTier(a,"s","PREFERRED");verify(db,never()).update(anyString(),any(Object[].class));verify(suppliers,never()).audit(any(),anyString(),anyString(),anyString(),anyMap());}
}
