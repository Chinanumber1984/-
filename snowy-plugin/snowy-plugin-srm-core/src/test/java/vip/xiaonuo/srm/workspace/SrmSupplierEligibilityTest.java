package vip.xiaonuo.srm.workspace;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vip.xiaonuo.common.exception.CommonException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SrmSupplierEligibilityTest {
 @Test void newBusinessRequiresQualifiedSupplierInTrustedOrganization(){var db=mock(JdbcTemplate.class);var guard=new SrmSupplierEligibility(db);when(db.queryForList(anyString(),eq("supplier"),eq("org"))).thenReturn(List.of(Map.of("STATUS","QUALIFIED")));assertDoesNotThrow(()->guard.requireEligibleForNewBusiness("supplier","org"));for(String state:List.of("POTENTIAL","CORRECTION","REVIEWING","FROZEN","ELIMINATED")){when(db.queryForList(anyString(),eq("supplier"),eq("org"))).thenReturn(List.of(Map.of("STATUS",state)));assertThrows(CommonException.class,()->guard.requireEligibleForNewBusiness("supplier","org"));}when(db.queryForList(anyString(),eq("supplier"),eq("other-org"))).thenReturn(List.of());assertThrows(CommonException.class,()->guard.requireEligibleForNewBusiness("supplier","other-org"));assertThrows(CommonException.class,()->guard.requireEligibleForNewBusiness("supplier",null));}
}
