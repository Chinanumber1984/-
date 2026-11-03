package vip.xiaonuo.flow;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SrmBpmnValidatorTest {
    private static final String GRAPHICAL_ONBOARD = """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" targetNamespace="srm.lifecycle">
          <process id="srm_onboard" name="准入审批流程" isExecutable="true">
            <startEvent id="start"/><sequenceFlow id="s1" sourceRef="start" targetRef="sqe"/>
            <userTask id="sqe" name="SQE 资质审核" flowable:candidateGroups="SQE"/><sequenceFlow id="s2" sourceRef="sqe" targetRef="manager"/>
            <userTask id="manager" name="采购经理审批" flowable:candidateGroups="CAT-MGR"><extensionElements><flowable:taskListener event="complete" delegateExpression="${srmFlowCompletion}"/></extensionElements></userTask>
            <sequenceFlow id="s3" sourceRef="manager" targetRef="end"/><endEvent id="end"/>
          </process>
          <bpmndi:BPMNDiagram id="diagram"><bpmndi:BPMNPlane id="plane" bpmnElement="srm_onboard">
            <bpmndi:BPMNShape id="shape_start" bpmnElement="start"><dc:Bounds x="100" y="100" width="36" height="36"/></bpmndi:BPMNShape>
            <bpmndi:BPMNEdge id="edge_1" bpmnElement="s1"><di:waypoint x="136" y="118"/><di:waypoint x="180" y="118"/></bpmndi:BPMNEdge>
          </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
        </definitions>
        """;

    @Test void acceptsGraphicalLayoutForConstrainedTemplate() {
        assertDoesNotThrow(() -> SrmBpmnValidator.validate("ONBOARD", GRAPHICAL_ONBOARD));
    }

    @Test void rejectsExecutableElementsEvenWhenDiagramIsPresent() {
        var unsafe = GRAPHICAL_ONBOARD.replace("<startEvent id=\"start\"/>", "<serviceTask id=\"start\"/><startEvent id=\"start\"/>");
        assertThrows(RuntimeException.class, () -> SrmBpmnValidator.validate("ONBOARD", unsafe));
    }

    @Test void rejectsDiagramNamespaceSpoofing() {
        var unsafe = GRAPHICAL_ONBOARD.replace("xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"", "xmlns:bpmndi=\"https://example.invalid/\"");
        assertThrows(RuntimeException.class, () -> SrmBpmnValidator.validate("ONBOARD", unsafe));
    }
}
