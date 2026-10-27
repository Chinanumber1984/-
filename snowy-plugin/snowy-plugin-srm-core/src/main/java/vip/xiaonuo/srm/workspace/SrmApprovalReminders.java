package vip.xiaonuo.srm.workspace;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import vip.xiaonuo.flow.api.SrmFlowApi;
import vip.xiaonuo.dev.api.DevMessageApi;
import static vip.xiaonuo.srm.workspace.SrmWorkspaceService.*;
@Component @RequiredArgsConstructor
public class SrmApprovalReminders {
 private final JdbcTemplate db;private final SrmApprovals approvals;private final SrmFlowApi flow;private final DevMessageApi messages;
 @Value("${srm.approval.reminder-hours:48}")private int hours;
 @Scheduled(cron="${srm.approval.reminder-cron:0 0 * * * *}",zone="Asia/Shanghai") @Transactional public void scan(){
  require(hours>=1&&hours<=720,500,"审批催办时限配置无效");
  for(var l:db.queryForList("SELECT * FROM SRM_APPROVAL_LINK WHERE STATUS='PENDING' ORDER BY CREATED_AT LIMIT 1000 FOR UPDATE"))for(var t:flow.tasks(str(l,"PROCESS_INSTANCE_ID"))){
   if(!((java.util.Date)t.get("createdAt")).toInstant().isBefore(Instant.now().minus(Duration.ofHours(hours))))continue;
   String role=t.get("node").equals("sqe")?"SQE":"CAT-MGR";if(t.get("node").equals("ADD_SIGN"))role=flow.task(str(t,"parentTaskId")).get("node").equals("sqe")?"SQE":"CAT-MGR";
   var targets=approvals.eligible(l,role);if(!str(t,"assignee").isBlank())targets=targets.stream().filter(u->u.equals(t.get("assignee"))).toList();
   if(targets.isEmpty())continue;
   int n=db.update("INSERT IGNORE INTO SRM_TASK_REMINDER(TASK_ID,REMINDER_DAY,CREATED_AT) VALUES(?,?,NOW(3))",t.get("id"),today());
   if(n>0)messages.sendMessageWithContent(targets,"SRM审批超时催办","申请："+l.get("ID")+"；节点："+t.get("name")+"，请及时处理。");
  }
 }
}
