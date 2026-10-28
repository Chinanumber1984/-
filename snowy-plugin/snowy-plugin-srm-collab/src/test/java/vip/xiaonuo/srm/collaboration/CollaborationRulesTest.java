package vip.xiaonuo.srm.collaboration;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static vip.xiaonuo.srm.collaboration.CollaborationRules.*;
class CollaborationRulesTest {
 BigDecimal d(String s){return new BigDecimal(s);}
 @Test void partialReceiptDoesNotCreatePrematureShortage(){assertFalse(differs(d("100"),d("20"),d("3"),false));assertTrue(differs(d("100"),d("20"),d("3"),true));}
 @Test void thresholdIsStrictAndOverageIsImmediate(){assertFalse(differs(d("100"),d("103"),d("3"),false));assertTrue(differs(d("100"),d("103.001"),d("3"),false));assertFalse(differs(d("100"),d("97"),d("3"),true));assertTrue(differs(d("100"),d("96.999"),d("3"),true));}
 @Test void moneyNeverUsesFloatingPointOrImplicitInvoiceRounding(){assertEquals(d("0.11"),line(d("1.5"),d("0.07")));invoiceMatches(d("100.00"),d("100"));assertThrows(RuntimeException.class,()->invoiceMatches(d("100"),d("99.99")));assertThrows(RuntimeException.class,()->money(d("100.001")));assertThrows(RuntimeException.class,()->money(d("-1")));}
 @Test void orgAndSupplierScopesCannotComeFromPayload(){var x=new CollaborationStore(null,null,null,null,null,null,null,null);var a=new vip.xiaonuo.srm.api.SrmSupplierAccessApi.Context("u","o","s",java.util.Set.of("SHIP"),false,java.util.Set.of("o"),false,true);assertTrue(x.scope(a,java.util.Map.of("ORG_ID","o","SUPPLIER_ID","s")));assertFalse(x.scope(a,java.util.Map.of("ORG_ID","other","SUPPLIER_ID","s")));assertFalse(x.scope(a,java.util.Map.of("ORG_ID","o","SUPPLIER_ID","other")));}
}
