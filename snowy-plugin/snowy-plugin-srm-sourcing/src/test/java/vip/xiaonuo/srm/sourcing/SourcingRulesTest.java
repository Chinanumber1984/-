package vip.xiaonuo.srm.sourcing;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static vip.xiaonuo.srm.sourcing.SourcingRules.*;
class SourcingRulesTest {
 BigDecimal d(String s){return new BigDecimal(s);}
 @Test void integerCentsBoundedForLuaAndJavascript(){assertEquals(9000000000000L,cents(d("90000000000.00")));for(String s:List.of("0","-1","1.001","90000000000.01"))assertThrows(RuntimeException.class,()->cents(d(s)));}
 @Test void roundsEachLineBeforeAddingFreight(){assertEquals(d("1.11"),total(List.of(d("0.335"),d("0.335")),List.of(d("1"),d("2")),d("0.10")));}
 @Test void minimumDecreaseAndExactDeadline(){assertThrows(RuntimeException.class,()->bidEnd(100,0,100,5,1000,10,990));assertThrows(RuntimeException.class,()->bidEnd(99,0,100,5,1000,10,991));assertEquals(100,bidEnd(99,0,100,0,1000,10,990));}
 @Test void unlimitedExtensionsOnlyInLastWindow(){long end=600000;for(int i=0;i<100;i++){long next=bidEnd(end-1,0,end,5,100000-i*10,10,99990-i*10);assertEquals(end+300000,next);end=next;}assertEquals(600000,bidEnd(1,0,600000,5,1000,10,990));}
 @Test void actualHistoryAndOfferCostFormula(){assertEquals(d("80.000000"),costScore(d("80"),d("100")));assertEquals(d("88.0000"),weighted(d("80"),List.of(d("90"),d("90"),d("1"),d("90")),List.of(d("30"),d("35"),d("20"),d("15"))));}
}
