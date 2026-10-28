package vip.xiaonuo.srm.exchange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExchangeSignatureTest {
 @Test void signatureBindsMethodPathBodyTimeAndNonce(){String key="test-key-test-key-test-key-test-key",method="POST",path="/srm-integration/v1/ingress/1",time="1700000000000",nonce="0123456789abcdef",body="{\"amount\":1}";String sig=ExchangeStore.signature(key,method,path,time,nonce,body);assertEquals(64,sig.length());assertEquals(sig,ExchangeStore.signature(key,method,path,time,nonce,body));assertNotEquals(sig,ExchangeStore.signature(key,"GET",path,time,nonce,body));assertNotEquals(sig,ExchangeStore.signature(key,method,path+"2",time,nonce,body));assertNotEquals(sig,ExchangeStore.signature(key,method,path,time,nonce,body+" "));assertNotEquals(sig,ExchangeStore.signature(key,method,path,time+"1",nonce,body));assertNotEquals(sig,ExchangeStore.signature(key,method,path,time,nonce+"1",body));}
}
