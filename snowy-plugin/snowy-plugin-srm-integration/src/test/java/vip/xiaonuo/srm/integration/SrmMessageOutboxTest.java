package vip.xiaonuo.srm.integration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SrmMessageOutboxTest {
 @Test void rejectsRedirectsAndUntrustedDestinations(){for(String u:new String[]{"http://qyapi.weixin.qq.com/a","https://evil.invalid/","https://qyapi.weixin.qq.com.evil.invalid/","https://name@qyapi.weixin.qq.com/a","https://qyapi.weixin.qq.com:8443/a"})assertThrows(IllegalArgumentException.class,()->SrmMessageOutbox.validated(u));assertEquals("qyapi.weixin.qq.com",SrmMessageOutbox.validated("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=placeholder").getHost());}
 @Test void businessRejectionAndHttpFailuresAreNotSuccess(){assertTrue(SrmMessageOutbox.accepted(200,"{\"errcode\":0}"));assertFalse(SrmMessageOutbox.accepted(200,"{\"errcode\":45009}"));assertFalse(SrmMessageOutbox.accepted(429,"{\"errcode\":0}"));assertFalse(SrmMessageOutbox.accepted(302,"{\"errcode\":0}"));assertFalse(SrmMessageOutbox.accepted(200,"bad"));}

 @Test void remoteRejectionRetriesThenBecomesVisibleFailure()throws Exception{
  var db=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);var client=org.mockito.Mockito.mock(java.net.http.HttpClient.class);var response=org.mockito.Mockito.mock(java.net.http.HttpResponse.class);var worker=new SrmMessageOutbox(db);
  org.springframework.test.util.ReflectionTestUtils.setField(worker,"client",client);org.springframework.test.util.ReflectionTestUtils.setField(worker,"url","https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=placeholder");
  org.mockito.Mockito.when(response.statusCode()).thenReturn(200);org.mockito.Mockito.when(response.body()).thenReturn("{\"errcode\":45009}");org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(response);
  org.mockito.Mockito.when(db.queryForList(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(java.util.Map.of("ID","event-1","ATTEMPTS",0,"EVENT_TYPE","APPROVAL_TASK","BUSINESS_KEY","approval-1","EVENT_KEY","key-1")));worker.dispatch();
  org.mockito.Mockito.verify(db).update(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("RETRY"),org.mockito.ArgumentMatchers.eq(1),org.mockito.ArgumentMatchers.eq(60),org.mockito.ArgumentMatchers.eq("REMOTE_REJECTED"),org.mockito.ArgumentMatchers.eq("event-1"));
  org.mockito.Mockito.when(db.queryForList(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(java.util.Map.of("ID","event-2","ATTEMPTS",4,"EVENT_TYPE","APPROVAL_TASK","BUSINESS_KEY","approval-2","EVENT_KEY","key-2")));worker.dispatch();
  org.mockito.Mockito.verify(db).update(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("FAILED"),org.mockito.ArgumentMatchers.eq(5),org.mockito.ArgumentMatchers.eq(960),org.mockito.ArgumentMatchers.eq("REMOTE_REJECTED"),org.mockito.ArgumentMatchers.eq("event-2"));
 }
 @Test void missingConfigurationDoesNotConsumeMessages(){var db=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);var worker=new SrmMessageOutbox(db);org.springframework.test.util.ReflectionTestUtils.setField(worker,"url","");worker.dispatch();org.mockito.Mockito.verifyNoInteractions(db);}
}
