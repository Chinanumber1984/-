package vip.xiaonuo.flow.api;
import java.util.*;
/** Workflow engine boundary. Business authorization belongs to the calling aggregate. */
public interface SrmFlowApi {
 String start(String definitionKey,String businessKey,Map<String,Object> variables);
 List<Map<String,Object>> tasks(String processId);
 Map<String,Object> task(String taskId);
 void claim(String taskId,String userId);
 void complete(String taskId,Map<String,Object> variables);
 void stop(String processId,String reason);
 void transfer(String taskId,String userId);
 String addSign(String taskId,String userId);
 boolean ended(String processId);
 List<Map<String,Object>> history(String processId);
 List<Map<String,Object>> definitions();
 String publish(String kind);
 String publishXml(String kind,String xml);
 String exportXml(String definitionId);
 void suspend(String definitionId,boolean suspended);
}
