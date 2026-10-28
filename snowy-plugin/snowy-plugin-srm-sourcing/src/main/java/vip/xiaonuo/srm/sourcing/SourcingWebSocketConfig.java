package vip.xiaonuo.srm.sourcing;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.config.*;
import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocketMessageBroker
public class SourcingWebSocketConfig implements WebSocketMessageBrokerConfigurer {
 private final ObjectProvider<SourcingSocketHub> hub;
 public SourcingWebSocketConfig(ObjectProvider<SourcingSocketHub> hub){this.hub=hub;}
 @Override public void registerStompEndpoints(StompEndpointRegistry r){r.addEndpoint("/srm-sourcing-ws").setAllowedOrigins("http://localhost:18080","http://127.0.0.1:18080","http://localhost:5175","http://127.0.0.1:5175");}
 @Override public void configureMessageBroker(MessageBrokerRegistry r){r.enableSimpleBroker("/queue");r.setApplicationDestinationPrefixes("/app");r.setUserDestinationPrefix("/user");r.setPreservePublishOrder(true);}
 @Override public void configureClientInboundChannel(ChannelRegistration r){r.interceptors(new ChannelInterceptor(){@Override public Message<?> preSend(Message<?> m,MessageChannel c){return hub.getObject().intercept(m);}});}
 @Override public void configureWebSocketTransport(WebSocketTransportRegistration r){r.setMessageSizeLimit(16384).setSendBufferSizeLimit(512*1024).setSendTimeLimit(5000).setTimeToFirstMessage(10000);}
}
