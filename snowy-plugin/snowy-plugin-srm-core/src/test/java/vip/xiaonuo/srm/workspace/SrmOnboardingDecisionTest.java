package vip.xiaonuo.srm.workspace;

import org.junit.jupiter.api.Test;
import vip.xiaonuo.common.exception.CommonException;
import static org.junit.jupiter.api.Assertions.*;

class SrmOnboardingDecisionTest {
 @Test void recordsStructuredApprovalEvidence(){
  assertEquals("[准入决策] 品类必要性=必要；绩效结论=通过\n理由",SrmApprovals.onboardingDecisionComment("APPROVE","REQUIRED","ACCEPT","理由"));
  assertEquals("[准入决策] 品类必要性=非必要；绩效结论=有条件通过\n理由",SrmApprovals.onboardingDecisionComment("APPROVE","NOT_REQUIRED","CONDITIONAL","理由"));
  assertEquals("[准入决策] 品类必要性=必要；绩效结论=不通过\n理由",SrmApprovals.onboardingDecisionComment("REJECT","REQUIRED","REJECT","理由"));
 }

 @Test void rejectsMissingOrContradictoryDecision(){
  assertThrows(CommonException.class,()->SrmApprovals.onboardingDecisionComment("APPROVE",null,"ACCEPT","理由"));
  assertThrows(CommonException.class,()->SrmApprovals.onboardingDecisionComment("APPROVE","REQUIRED",null,"理由"));
  assertThrows(CommonException.class,()->SrmApprovals.onboardingDecisionComment("APPROVE","REQUIRED","REJECT","理由"));
  assertThrows(CommonException.class,()->SrmApprovals.onboardingDecisionComment("REJECT","REQUIRED","ACCEPT","理由"));
  assertThrows(CommonException.class,()->SrmApprovals.onboardingDecisionComment("APPROVE","INVALID","ACCEPT","理由"));
 }
}
