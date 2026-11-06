package vip.xiaonuo.srm.workspace;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.flow.api.SrmFlowApi;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SrmWorkflowCategoryServiceTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final SrmFlowApi flow=mock(SrmFlowApi.class);
 private final SrmWorkflowCategoryService service=new SrmWorkflowCategoryService(db,flow);
 private final SrmActor admin=actor("admin",Set.of("SYS-ADMIN"),false);

 private static SrmActor actor(String user,Set<String> roles,boolean portal){return new SrmActor(user,"org",portal?"supplier":null,roles,false,Set.of("org"),false,false,portal);}

 @Test void listReturnsTheEightSupportedOneToOneMappings(){
  var rows=SrmWorkflowCategoryService.BUSINESS_TYPES.stream().map(type->Map.<String,Object>of("businessType",type,"categoryCode",type,"categoryName",type,"rowVersion",0L)).toList();
  when(db.queryForList(anyString())).thenReturn(rows);
  assertEquals(8,service.list(actor("staff",Set.of(),false)).size());
  assertThrows(CommonException.class,()->service.list(actor("portal",Set.of(),true)));
  when(db.queryForList(anyString())).thenReturn(rows.subList(0,7));
  assertThrows(CommonException.class,()->service.list(actor("staff",Set.of(),false)));
 }

 @Test void onlyInternalWorkflowAdminsCanMaintainMapping(){
  var update=new SrmWorkflowCategoryInputs.Update(0,"NEW_CODE","New category");
  assertThrows(CommonException.class,()->service.update(actor("staff",Set.of("SQE"),false),"ONBOARD",update));
  assertThrows(CommonException.class,()->service.update(actor("portal",Set.of("SYS-ADMIN"),true),"ONBOARD",update));
  verifyNoInteractions(db,flow);
 }

 @Test void mappingRejectsDuplicateCodeOrNameAcrossBusinessTypes(){
  when(db.queryForList(contains("FOR UPDATE"),eq("ONBOARD"))).thenReturn(List.of(Map.of("ROW_VERSION",0L)));
  when(db.queryForObject(contains("COUNT(*)"),eq(Long.class),eq("ONBOARD"),eq("CHANGE"),eq("New category"))).thenReturn(1L);
  assertThrows(CommonException.class,()->service.update(admin,"ONBOARD",new SrmWorkflowCategoryInputs.Update(0,"CHANGE","New category")));
  when(db.queryForObject(contains("COUNT(*)"),eq(Long.class),eq("ONBOARD"),eq("ONBOARD_V2"),eq("资料变更"))).thenReturn(1L);
  assertThrows(CommonException.class,()->service.update(admin,"ONBOARD",new SrmWorkflowCategoryInputs.Update(0,"ONBOARD_V2","资料变更")));
  verify(db,never()).update(anyString(),any(Object[].class));
  verifyNoInteractions(flow);
 }

 @Test void staleVersionAndUnsupportedBusinessTypeAreRejected(){
  assertThrows(CommonException.class,()->service.update(admin,"OTHER",new SrmWorkflowCategoryInputs.Update(0,"NEW_CODE","New category")));
  assertThrows(CommonException.class,()->service.update(admin,"ONBOARD",new SrmWorkflowCategoryInputs.Update(0,"not-valid","New category")));
  when(db.queryForList(contains("FOR UPDATE"),eq("ONBOARD"))).thenReturn(List.of(Map.of("ROW_VERSION",1L)));
  assertThrows(CommonException.class,()->service.update(admin,"ONBOARD",new SrmWorkflowCategoryInputs.Update(0,"NEW_CODE","New category")));
  verifyNoInteractions(flow);
 }

 @Test void adminUpdatePersistsVersionAndUpdatesAllProcessDefinitionCategories(){
  when(db.queryForList(contains("FOR UPDATE"),eq("ONBOARD"))).thenReturn(List.of(Map.of("ROW_VERSION",2L)));
  when(db.queryForObject(contains("COUNT(*)"),eq(Long.class),eq("ONBOARD"),eq("ONBOARD_V2"),eq("Supplier intake"))).thenReturn(0L);
  when(db.update(anyString(),any(Object[].class))).thenReturn(1);
  var result=service.update(admin,"ONBOARD",new SrmWorkflowCategoryInputs.Update(2,"ONBOARD_V2","Supplier intake"));
  assertEquals("ONBOARD",result.get("businessType"));
  assertEquals("ONBOARD_V2",result.get("categoryCode"));
  assertEquals(3L,result.get("rowVersion"));
  verify(db).update(contains("ROW_VERSION=ROW_VERSION+1"),eq("ONBOARD_V2"),eq("Supplier intake"),eq("admin"),eq("ONBOARD"),eq(2L));
  verify(flow).updateCategory("ONBOARD","ONBOARD_V2");
 }
}
