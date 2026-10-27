package vip.xiaonuo.srm.workspace;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SrmCaptchaNonceTest {
 @Test void registrationAcceptsLegacyAndClockIndependentCaptchaNonces(){try(var factory=Validation.buildDefaultValidatorFactory()){var validator=factory.getValidator();for(String nonce:new String[]{"1234567890123456789","d59547293b904cf4822b053fe1825ed2"})assertTrue(validator.validateValue(SrmRegistration.Register.class,"validCodeReqNo",nonce).isEmpty());for(String nonce:new String[]{"bad-nonce","abc","../../cache",""})assertFalse(validator.validateValue(SrmRegistration.Register.class,"validCodeReqNo",nonce).isEmpty());}}
}
