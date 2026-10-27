package vip.xiaonuo.srm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.xiaonuo.srm.modular.supplier.entity.SrmSupplier;
import vip.xiaonuo.srm.modular.supplier.mapper.SrmSupplierMapper;
import vip.xiaonuo.srm.modular.supplier.service.impl.SrmSupplierServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupplierReadTest {
    @Test void missingOrganizationFailsBeforeDatabaseAccess() {
        var mapper = mock(SrmSupplierMapper.class);
        assertThrows(IllegalStateException.class, () -> new SrmSupplierServiceImpl(mapper).page("", 1, 20, ""));
        verifyNoInteractions(mapper);
    }
    @Test void sensitiveColumnsAreNeverSerialized() throws Exception {
        var supplier = new SrmSupplier();
        supplier.setCreditCode("sensitive-credit"); supplier.setBankAccount("sensitive-bank");
        String json = new ObjectMapper().writeValueAsString(supplier);
        assertFalse(json.contains("sensitive"));
        assertFalse(json.contains("creditCode")); assertFalse(json.contains("bankAccount"));
    }
}
