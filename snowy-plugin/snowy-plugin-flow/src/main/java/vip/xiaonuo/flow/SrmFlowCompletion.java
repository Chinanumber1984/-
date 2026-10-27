package vip.xiaonuo.flow;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import org.flowable.task.service.delegate.*;
import lombok.RequiredArgsConstructor;
import vip.xiaonuo.flow.api.SrmFlowCompleted;
@Component("srmFlowCompletion") @RequiredArgsConstructor
public class SrmFlowCompletion implements TaskListener {
 private final ApplicationEventPublisher events;
 public void notify(DelegateTask t){events.publishEvent(new SrmFlowCompleted(t.getProcessInstanceId(),String.valueOf(t.getVariable("approvalId")),String.valueOf(t.getVariable("decisionActor"))));}
}
