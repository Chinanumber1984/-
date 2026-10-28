package vip.xiaonuo.flow;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import vip.xiaonuo.common.exception.CommonException;
/** Deliberately constrained lifecycle graph. No executable code, service tasks, external entities or arbitrary expressions. */
final class SrmBpmnValidator {
 static final String MODEL="http://www.omg.org/spec/BPMN/20100524/MODEL",FLOW="http://flowable.org/bpmn";
 static String validate(String kind,String xml){try{
  if(!Set.of("ONBOARD","CHANGE","FREEZE","UNFREEZE","ELIMINATE","AWARD","COL_REVIEW","QUALITY_REVIEW").contains(kind)||xml.length()>100000)throw new IllegalArgumentException();
  var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");var doc=f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  var all=doc.getElementsByTagName("*");var ids=new HashSet<String>();var edges=new HashSet<String>();int processes=0,listeners=0;Element manager=null;
  Set<String> expected=kind.equals("ONBOARD")?Set.of("start","sqe","manager","end"):Set.of("start","manager","end");Set<String> expectedEdges=kind.equals("ONBOARD")?Set.of("start>sqe","sqe>manager","manager>end"):Set.of("start>manager","manager>end");
  for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);String n=e.getLocalName(),ns=e.getNamespaceURI();if(!MODEL.equals(ns)&&!(FLOW.equals(ns)&&n.equals("taskListener")))throw new IllegalArgumentException();if(!Set.of("definitions","process","startEvent","endEvent","userTask","sequenceFlow","extensionElements","taskListener").contains(n))throw new IllegalArgumentException();
   var attrs=e.getAttributes();for(int j=0;j<attrs.getLength();j++){var a=attrs.item(j);String name=a.getNodeName();if(!Set.of("xmlns","xmlns:flowable","targetNamespace","id","name","isExecutable","sourceRef","targetRef","flowable:candidateGroups","event","delegateExpression").contains(name))throw new IllegalArgumentException();if(name.equals("xmlns")&&!a.getNodeValue().equals(MODEL)||name.equals("xmlns:flowable")&&!a.getNodeValue().equals(FLOW))throw new IllegalArgumentException();if(a.getNodeValue().contains("${")&&!n.equals("taskListener"))throw new IllegalArgumentException();}
   if(n.equals("process")){processes++;if(!e.getAttribute("id").equals("srm_"+kind.toLowerCase(Locale.ROOT))||!e.getAttribute("isExecutable").equals("true"))throw new IllegalArgumentException();}
   if(Set.of("startEvent","endEvent","userTask").contains(n)){String id=e.getAttribute("id");if(!ids.add(id)||!expected.contains(id))throw new IllegalArgumentException();if(n.equals("startEvent")&&!id.equals("start")||n.equals("endEvent")&&!id.equals("end"))throw new IllegalArgumentException();if(n.equals("userTask")){String role=id.equals("sqe")||kind.equals("QUALITY_REVIEW")?"SQE":"CAT-MGR";if(!e.getAttributeNS(FLOW,"candidateGroups").equals(role))throw new IllegalArgumentException();if(id.equals("manager"))manager=e;}}
   if(n.equals("sequenceFlow")&&!edges.add(e.getAttribute("sourceRef")+">"+e.getAttribute("targetRef")))throw new IllegalArgumentException();
   if(n.equals("taskListener")){listeners++;if(!e.getAttribute("event").equals("complete")||!e.getAttribute("delegateExpression").equals("${srmFlowCompletion}")||!((Element)e.getParentNode().getParentNode()).getAttribute("id").equals("manager"))throw new IllegalArgumentException();}
  }
  if(processes!=1||!ids.equals(expected)||!edges.equals(expectedEdges)||manager==null||listeners>1)throw new IllegalArgumentException();
  if(listeners==0){var extension=doc.createElementNS(MODEL,"extensionElements");var listener=doc.createElementNS(FLOW,"flowable:taskListener");listener.setAttribute("event","complete");listener.setAttribute("delegateExpression","${srmFlowCompletion}");extension.appendChild(listener);manager.appendChild(extension);}
  var tf=TransformerFactory.newInstance();tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");var out=new java.io.StringWriter();tf.newTransformer().transform(new DOMSource(doc),new StreamResult(out));return out.toString();
 }catch(Exception ex){throw new CommonException(400,"流程文件不符合受控生命周期模板：禁止脚本、服务任务、外部实体及任意表达式，节点与候选角色必须匹配业务分类");}}
}
