package vip.xiaonuo.srm.performance;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import vip.xiaonuo.srm.api.SrmSupplierAccessApi;
import vip.xiaonuo.common.exception.CommonException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PerformanceDetailTest {
 private static Map<String,Object> score(){
  var r=new HashMap<String,Object>();r.put("ID","score");r.put("SUPPLIER_ID","supplier");r.put("PERIOD","2026-01");r.put("INPUT_ID","input-1");r.put("TEMPLATE_ID",1L);r.put("DELIVER_SCORE",80);r.put("QUALITY_SCORE",90);r.put("COST_SCORE",70);r.put("SERVICE_SCORE",60);r.put("TOTAL",76);r.put("GRADE","QUALIFIED");r.put("ROW_VERSION",1L);return r;
 }
 private static Map<String,Object> input(){return Map.of("id","input-1","revision",2L,"sourceType","HISTORICAL","sourceRef","LEDGER-2026-01","scores","[80,90,70,60]","importId","batch-1","createdBy","operator-1","createdAt","2026-02-01 03:00:00.000");}
 private PerformanceService service(JdbcTemplate db,SrmSupplierAccessApi access){
  when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(inv->{String sql=inv.getArgument(0);if(sql.contains("FROM SRM_SCORECARD"))return List.of(score());if(sql.contains("FROM SRM_PERF_INPUT"))return List.of(input());return List.of();});
  when(access.requireSupplier(any(),eq("supplier"),eq(false))).thenReturn(new SrmSupplierAccessApi.Supplier("supplier","org","供方","CAT","QUALIFIED","QUALIFIED"));
  return new PerformanceService(db,access,new ObjectMapper());
 }
 @Test void internalDetailExposesExactInputAndImportEvidence(){
  var db=mock(JdbcTemplate.class);var access=mock(SrmSupplierAccessApi.class);var actor=new SrmSupplierAccessApi.Context("operator","org",null,Set.of("CAT-MGR"),true,Set.of(),false,false);var result=service(db,access).detail(actor,"score");
  @SuppressWarnings("unchecked") var source=(Map<String,Object>)result.get("sourceEvidence");
  assertEquals("input-1",source.get("id"));assertEquals(2L,source.get("revision"));assertEquals("LEDGER-2026-01",source.get("sourceRef"));assertEquals("batch-1",source.get("importId"));assertEquals("operator-1",source.get("createdBy"));assertEquals(List.of(new BigDecimal("80"),new BigDecimal("90"),new BigDecimal("70"),new BigDecimal("60")),source.get("scores"));verify(db).queryForList(contains("FROM SRM_PERF_INPUT"),eq("input-1"),eq("supplier"),eq("2026-01"));
 }
 @Test void portalDetailKeepsOwnSourceTraceButHidesInternalImportActor(){
  var access=mock(SrmSupplierAccessApi.class);var actor=new SrmSupplierAccessApi.Context("contact","org","supplier",Set.of("ADMIN"),false,Set.of(),false,true);var result=service(mock(JdbcTemplate.class),access).detail(actor,"score");
  @SuppressWarnings("unchecked") var source=(Map<String,Object>)result.get("sourceEvidence");
  assertEquals("input-1",source.get("id"));assertEquals("LEDGER-2026-01",source.get("sourceRef"));assertTrue(source.containsKey("scores"));assertFalse(source.containsKey("importId"));assertFalse(source.containsKey("createdBy"));
 }
 @Test void crossSupplierPortalIsRejectedBeforeSourceEvidenceQuery(){
  var db=mock(JdbcTemplate.class);var access=mock(SrmSupplierAccessApi.class);var actor=new SrmSupplierAccessApi.Context("contact","org","other-supplier",Set.of("ADMIN"),false,Set.of(),false,true);var service=service(db,access);
  when(access.requireSupplier(eq(actor),eq("supplier"),eq(false))).thenThrow(new CommonException(404,"not visible"));
  assertThrows(CommonException.class,()->service.detail(actor,"score"));verify(access).requireSupplier(actor,"supplier",false);verify(db,never()).queryForList(contains("FROM SRM_PERF_INPUT"),any(Object[].class));
 }
}
