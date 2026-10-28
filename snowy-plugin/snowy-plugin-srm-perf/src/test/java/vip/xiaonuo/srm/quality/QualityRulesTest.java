package vip.xiaonuo.srm.quality;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.math.BigDecimal;import java.util.*;
class QualityRulesTest {
 @Test void deductionsAreExactCappedAndCannotIncreaseScores(){assertEquals(new BigDecimal("74.75"),QualityRules.qualityScore(List.of(new BigDecimal("20"),new BigDecimal("5.25"))));assertEquals(BigDecimal.ZERO,QualityRules.qualityScore(List.of(new BigDecimal("80"),new BigDecimal("30"))));assertThrows(RuntimeException.class,()->QualityRules.qualityScore(List.of(new BigDecimal("-1"))));}
 @Test void repliedVerifiedAndClosedScarsAreNotOverdueReplies(){for(String state:List.of("OPEN","RETURNED","ESCALATED")){assertTrue(QualityRules.responseOverdue(state,99,100));assertFalse(QualityRules.responseOverdue(state,100,100));}for(String state:List.of("SUBMITTED","VERIFIED","CLOSED","REJECTED"))assertFalse(QualityRules.responseOverdue(state,1,100));}
}
