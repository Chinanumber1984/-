package vip.xiaonuo.srm.workspace;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vip.xiaonuo.client.ClientUserApi;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.srm.foundation.SensitiveFieldCodec;
import vip.xiaonuo.flow.api.SrmFlowApi;
import vip.xiaonuo.sys.api.SysUserApi;
import vip.xiaonuo.dev.api.DevMessageApi;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SrmWorkspaceCriticalEditTest {
 private static final String SID="supplier-1";
 private static final SrmActor ADMIN=new SrmActor("admin","org-1",null,Set.of("superAdmin"),true,Set.of(),false,true,false);

 private static SensitiveFieldCodec codec(){byte[] enc=new byte[16],mac=new byte[32],index=new byte[32];Arrays.fill(mac,(byte)1);Arrays.fill(index,(byte)2);return new SensitiveFieldCodec("test-key",enc,mac,index);}
 private static Map<String,Object> supplier(SensitiveFieldCodec codec,String status){return Map.ofEntries(Map.entry("ID",SID),Map.entry("DELETE_FLAG","NOT_DELETE"),Map.entry("ORG_ID","org-1"),Map.entry("CREATE_USER","admin"),Map.entry("STATUS",status),Map.entry("ROW_VERSION",0L),Map.entry("NAME","Old supplier"),Map.entry("CATEGORY","CAT"),Map.entry("ADDRESS","old address"),Map.entry("BANK_NAME","Old bank"),Map.entry("CREDIT_CODE",codec.encrypt("old-credit")),Map.entry("BANK_ACCOUNT",codec.encrypt("old-account")));}

 @Test void directEditRejectsCriticalChangesInEveryEditableStatus(){
  for(String status:List.of("POTENTIAL","CORRECTION","QUALIFIED","FROZEN")){
   for(var changed:List.of(
    new SrmInputs.SupplierEdit("Changed supplier","CAT",null,null,"Old bank","old address",0),
    new SrmInputs.SupplierEdit("Old supplier","CAT","000000000000000000",null,"Old bank","old address",0),
    new SrmInputs.SupplierEdit("Old supplier","OTHER",null,null,"Old bank","old address",0),
    new SrmInputs.SupplierEdit("Old supplier","CAT",null,"new-account","Old bank","old address",0),
    new SrmInputs.SupplierEdit("Old supplier","CAT",null,null,"New bank","old address",0))) {
    var db=mock(JdbcTemplate.class);var codec=codec();var service=new SrmWorkspaceService(db,codec,mock(ClientUserApi.class));
    when(db.queryForList(anyString(),eq(SID))).thenReturn(List.of(supplier(codec,status)));
    when(db.queryForObject(contains("SRM_APPROVAL_LINK"),eq(Long.class),eq(SID))).thenReturn(0L);
    when(db.queryForObject(contains("SRM_CATEGORY"),eq(Long.class),eq("OTHER"))).thenReturn(1L);
    assertThrows(CommonException.class,()->service.edit(ADMIN,SID,changed),status+" critical field");
    verify(db,never()).update(startsWith("UPDATE SRM_SUPPLIER SET NAME="),any(Object[].class));
   }
  }
 }

 @Test void directAddressEditIsRetainedAndAudited(){
  var db=mock(JdbcTemplate.class);var codec=codec();var service=spy(new SrmWorkspaceService(db,codec,mock(ClientUserApi.class)));var row=supplier(codec,"POTENTIAL");
  doReturn(row).when(service).supplier(ADMIN,SID,true);
  when(db.queryForObject(contains("SRM_APPROVAL_LINK"),eq(Long.class),eq(SID))).thenReturn(0L);
  when(db.queryForMap(anyString(),eq(SID))).thenReturn(row);
  when(db.update(anyString(),any(Object[].class))).thenReturn(1);
  doNothing().when(service).profileVersion(any(),anyMap(),anyMap());
  doNothing().when(service).audit(any(),anyString(),anyString(),anyString(),anyMap());
  doReturn(Map.of("id",SID)).when(service).detail(ADMIN,SID);

  var result=service.edit(ADMIN,SID,new SrmInputs.SupplierEdit("Old supplier","CAT",null,null,"Old bank","new address",0));
  assertEquals(SID,result.get("id"));
  var args=org.mockito.ArgumentCaptor.forClass(Object[].class);
  verify(db).update(contains("UPDATE SRM_SUPPLIER SET NAME=?"),args.capture());
  assertEquals("new address",args.getValue()[2]);
  verify(service).profileVersion(eq(ADMIN),eq(row),anyMap());
  verify(service).audit(eq(ADMIN),eq(SID),eq(SID),eq("SUPPLIER_UPDATED"),argThat(m->Boolean.FALSE.equals(m.get("criticalChange"))));
 }

 @Test void changeFlowCanActuallyStartForEveryEditableState(){
  for(String status:List.of("POTENTIAL","CORRECTION","QUALIFIED","FROZEN")){
   var db=mock(JdbcTemplate.class);var codec=codec();var suppliers=mock(SrmWorkspaceService.class);var flow=mock(SrmFlowApi.class);var actor=ADMIN;var row=supplier(codec,status);
   when(suppliers.supplier(actor,SID,true)).thenReturn(row);doNothing().when(suppliers).audit(any(),anyString(),anyString(),anyString(),anyMap());
   when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).contains("SRM_APPROVAL_LINK WHERE ID=?")?List.of(Map.of("ID","approval","SUPPLIER_ID",SID,"PROCESS_INSTANCE_ID","process","ORG_ID","org-1","REQUESTED_BY",actor.userId())):List.of());
   when(db.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).contains("SRM_CATEGORY")?1L:0L);
   when(db.update(anyString(),any(Object[].class))).thenReturn(1);
   when(flow.definitions()).thenReturn(List.of(Map.of("key","srm_change","suspended",false)));
   when(flow.start(eq("srm_change"),anyString(),anyMap())).thenReturn("process");
   when(flow.tasks("process")).thenReturn(List.of());
   var service=spy(new SrmApprovals(db,suppliers,flow,mock(SrmWorkflowCategoryService.class),codec,mock(SysUserApi.class),mock(DevMessageApi.class)));
   doReturn(Map.of("id","approval")).when(service).detail(eq(actor),anyString());
   var changes=new SrmInputs.SupplierEdit("New supplier","CAT",null,null,"New bank",null,0);
   var input=new SrmApprovalInputs.Submit("W38_CHANGE_0001","CHANGE",0,"资料变更原因",changes);
   assertEquals("approval",service.submit(actor,SID,input).get("id"),status);
   verify(flow).start(eq("srm_change"),anyString(),argThat(v->"CHANGE".equals(v.get("businessType"))));
   verify(db).update(startsWith("UPDATE SRM_SUPPLIER SET STATUS=?"),eq(status),eq(actor.userId()),eq(SID));
  }
 }
}
