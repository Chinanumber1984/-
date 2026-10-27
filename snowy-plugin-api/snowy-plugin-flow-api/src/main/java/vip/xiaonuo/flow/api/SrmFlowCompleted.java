package vip.xiaonuo.flow.api;
/** Synchronous TaskListener callback; exceptions must roll back the engine transaction. */
public record SrmFlowCompleted(String processId,String approvalId,String actorId) {}
