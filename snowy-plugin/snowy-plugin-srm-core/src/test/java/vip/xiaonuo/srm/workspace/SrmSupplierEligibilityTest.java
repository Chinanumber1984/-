package vip.xiaonuo.srm.workspace;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vip.xiaonuo.common.exception.CommonException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SrmSupplierEligibilityTest {
 @Test void newBusinessRequiresQualifiedSupplierInTrustedOrganization(){var db=mock(JdbcTemplate.class);var guard=new SrmSupplierEligibility(db,mock(SrmWorkspaceService.class));when(db.queryForList(anyString(),eq("supplier"),eq("org"))).thenReturn(List.of(Map.of("STATUS","QUALIFIED")));assertDoesNotThrow(()->guard.requireEligibleForNewBusiness("supplier","org"));for(String state:List.of("POTENTIAL","CORRECTION","REVIEWING","FROZEN","ELIMINATED")){when(db.queryForList(anyString(),eq("supplier"),eq("org"))).thenReturn(List.of(Map.of("STATUS",state)));assertThrows(CommonException.class,()->guard.requireEligibleForNewBusiness("supplier","org"));}when(db.queryForList(anyString(),eq("supplier"),eq("other-org"))).thenReturn(List.of());assertThrows(CommonException.class,()->guard.requireEligibleForNewBusiness("supplier","other-org"));assertThrows(CommonException.class,()->guard.requireEligibleForNewBusiness("supplier",null));}
 @Test void awardRequiresLiveCertificateCompleteness(){var db=mock(JdbcTemplate.class);var suppliers=mock(SrmWorkspaceService.class);var guard=new SrmSupplierEligibility(db,suppliers);var row=Map.<String,Object>of("ID","supplier","ORG_ID","org","CATEGORY","MACHINING");when(db.queryForList(anyString(),eq("supplier"),eq("org"))).thenReturn(List.of(Map.of("STATUS","QUALIFIED")));when(db.queryForMap(anyString(),eq("supplier"),eq("org"))).thenReturn(row);when(suppliers.qualification(row)).thenReturn(Map.of("complete",false,"missing",List.of("ISO9001")));assertThrows(CommonException.class,()->guard.requireEligibleForAward("supplier","org"));when(suppliers.qualification(row)).thenReturn(Map.of("complete",true,"missing",List.of()));assertDoesNotThrow(()->guard.requireEligibleForAward("supplier","org"));}
}
