package vip.xiaonuo.srm.workspace;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.common.annotation.CommonLog;
import static vip.xiaonuo.srm.workspace.SrmApprovalInputs.*;
@RestController @RequiredArgsConstructor @RequestMapping({"/srm/v1","/client/c/srm/v1"})
public class SrmApprovalController {
 private final SrmIdentity identity;private final SrmApprovals service;private final SrmWorkspaceService workspace;private final SrmApprovalReminders reminders;
 @PostMapping("/suppliers/{sid}/approvals") @CommonLog(value="SRM提交审批",safe=true) public CommonResult<?> submit(@PathVariable String sid,@Valid @RequestBody Submit p){return CommonResult.data(service.submit(identity.current(),sid,p));}
 @GetMapping("/suppliers/{sid}/approvals") public CommonResult<?> list(@PathVariable String sid){return CommonResult.data(service.list(identity.current(),sid));}
 @GetMapping("/approvals/tasks") public CommonResult<?> tasks(){return CommonResult.data(service.inbox(identity.current()));}
 @PostMapping("/approvals/{aid}/withdraw") @CommonLog(value="SRM撤回申请",safe=true) public CommonResult<?> withdraw(@PathVariable String aid,@Valid @RequestBody Withdrawal p){return CommonResult.data(service.withdraw(identity.current(),aid,p.comment()));}
 @GetMapping("/approvals/{aid}") public CommonResult<?> detail(@PathVariable String aid){return CommonResult.data(service.detail(identity.current(),aid));}
 @GetMapping("/approvals/{aid}/tasks/{tid}/candidates") public CommonResult<?> candidates(@PathVariable String aid,@PathVariable String tid){return CommonResult.data(service.candidates(identity.current(),aid,tid));}
 @PostMapping("/approvals/{aid}/tasks/{tid}") @CommonLog(value="SRM处理审批",safe=true) public CommonResult<?> decide(@PathVariable String aid,@PathVariable String tid,@Valid @RequestBody Decision p){return CommonResult.data(service.decide(identity.current(),aid,tid,p));}
 @GetMapping("/suppliers/{sid}/versions") public CommonResult<?> versions(@PathVariable String sid){return CommonResult.data(workspace.versions(identity.current(),sid));}
 @PostMapping("/maintenance/approval-reminders") @CommonLog(value="SRM审批催办扫描",safe=true) public CommonResult<?> reminders(){var a=identity.current();SrmWorkspaceService.require(a.superUser(),403,"仅系统管理员可执行全局扫描");reminders.scan();return CommonResult.ok();}
 @GetMapping("/maintenance/messages") public CommonResult<?> messages(){return CommonResult.data(service.messageStatus(identity.current()));}
 @PostMapping("/maintenance/messages/{id}/retry") @CommonLog(value="SRM重试失败消息",safe=true) public CommonResult<?> retry(@PathVariable String id){service.retryMessage(identity.current(),id);return CommonResult.ok();}
 @GetMapping("/workflow/definitions") public CommonResult<?> definitions(){return CommonResult.data(service.definitions(identity.current()));}
 @PostMapping("/workflow/definitions") @CommonLog(value="SRM发布流程版本",safe=true) public CommonResult<?> publish(@Valid @RequestBody Publication p){return CommonResult.data(service.publish(identity.current(),p.kind()));}
 @PostMapping("/workflow/definitions/upload") @CommonLog(value="SRM上传受控BPMN",safe=true) public CommonResult<?> upload(@RequestParam String kind,@RequestParam org.springframework.web.multipart.MultipartFile file)throws Exception{SrmWorkspaceService.require(file.getSize()<=100000,400,"流程文件最大100KB");return CommonResult.data(service.upload(identity.current(),kind,new String(file.getBytes(),java.nio.charset.StandardCharsets.UTF_8)));}
 @GetMapping(value="/workflow/definitions/{id}/xml",produces="application/xml") public String xml(@PathVariable String id){return service.exportXml(identity.current(),id);}
 @PostMapping("/workflow/definitions/{id}/suspension") @CommonLog(value="SRM挂起流程",safe=true) public CommonResult<?> suspension(@PathVariable String id,@RequestBody Suspension p){service.suspend(identity.current(),id,p.suspended());return CommonResult.ok();}
}
