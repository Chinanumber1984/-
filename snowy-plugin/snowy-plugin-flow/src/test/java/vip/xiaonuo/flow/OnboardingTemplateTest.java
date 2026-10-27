package vip.xiaonuo.flow;
import org.flowable.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OnboardingTemplateTest {
    @Test void templateRunsTwoReviewTasksInOrder() {
        var engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
            .setJdbcUrl("jdbc:h2:mem:srmflow;DB_CLOSE_DELAY=-1").setDatabaseSchemaUpdate("true")
            .setAsyncExecutorActivate(false).buildProcessEngine();
        try {
            engine.getRepositoryService().createDeployment().addClasspathResource("bpmn-templates/supplier-onboarding.bpmn20.xml").deploy();
            var instance = engine.getRuntimeService().startProcessInstanceByKey("srmSupplierOnboarding", "supplier-test");
            var tasks = engine.getTaskService();
            var first = tasks.createTaskQuery().processInstanceId(instance.getId()).singleResult();
            assertEquals("sqeReview", first.getTaskDefinitionKey()); tasks.complete(first.getId());
            var second = tasks.createTaskQuery().processInstanceId(instance.getId()).singleResult();
            assertEquals("managerApproval", second.getTaskDefinitionKey()); tasks.complete(second.getId());
            assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instance.getId()).count());
        } finally { engine.close(); }
    }
}
