/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.workbench.common.forms.jbpm.server.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.enterprise.context.Dependent;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.bpmn2.Assignment;
import org.eclipse.bpmn2.DataInput;
import org.eclipse.bpmn2.DataInputAssociation;
import org.eclipse.bpmn2.DataOutput;
import org.eclipse.bpmn2.DataOutputAssociation;
import org.eclipse.bpmn2.Definitions;
import org.eclipse.bpmn2.FlowElementsContainer;
import org.eclipse.bpmn2.FormalExpression;
import org.eclipse.bpmn2.ItemDefinition;
import org.eclipse.bpmn2.Process;
import org.eclipse.bpmn2.RootElement;
import org.eclipse.bpmn2.UserTask;
import org.jsoup.parser.Parser;
import org.kie.workbench.common.forms.jbpm.model.authoring.JBPMVariable;
import org.kie.workbench.common.forms.jbpm.model.authoring.process.BusinessProcessFormModel;
import org.kie.workbench.common.forms.jbpm.model.authoring.task.TaskFormModel;
import org.kie.workbench.common.forms.jbpm.server.service.BPMNFormModelGenerator;
import org.kie.workbench.common.forms.jbpm.service.bpmn.util.BPMNVariableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dependent
public class BPMNFormModelGeneratorImpl implements BPMNFormModelGenerator {

    private static final Logger logger = LoggerFactory.getLogger(BPMNFormModelGeneratorImpl.class);

    @Override
    public BusinessProcessFormModel generateProcessFormModel(Definitions source) {

        Process process = getProcess(source);

        if (process != null) {

            List<JBPMVariable> variables = new ArrayList<>();

            process.getProperties().forEach(prop -> {
                String varName = prop.getId();
                String varType = BPMNVariableUtils.getRealTypeForInput(prop.getItemSubjectRef().getStructureRef());

                variables.add(new JBPMVariable(varName,
                                               varType));
            });

            return new BusinessProcessFormModel(process.getId(),
                                                process.getName(),
                                                variables);
        }

        return null;
    }

    @Override
    public List<TaskFormModel> generateTaskFormModels(Definitions source) {

        Process process = getProcess(source);

        if (process != null) {
            ProcessTaskFormsGenerationResult result = readUserTaskFormVariables(process);
            return result.getAllTaskFormVariables().stream().filter(taskFormVariables -> {
                if (!taskFormVariables.isValid()) {
                    logger.warn(generateErrorMessage(taskFormVariables));
                    return false;
                }
                return true;
            }).map(TaskFormVariables::toFormModel).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public TaskFormModel generateTaskFormModel(Definitions source,
                                               String taskId) {
        Process process = getProcess(source);

        if (process != null) {

            ProcessTaskFormsGenerationResult generationResult = readUserTaskFormVariables(process);

            Optional<TaskFormVariables> resultTaskFormVariables = Optional.ofNullable(generationResult.getTaskFormVariablesByTaskId(taskId));

            if (resultTaskFormVariables.isPresent()) {
                TaskFormVariables formVariables = resultTaskFormVariables.get();

                if (!formVariables.isValid()) {
                    throw new IllegalStateException(generateErrorMessage(formVariables));
                }

                return formVariables.toFormModel();
            }
        }
        return null;
    }

    private String generateErrorMessage(TaskFormVariables formVariables) {
        StringBuffer buffer = new StringBuffer("Unable to generate form '")
                .append(formVariables.getTaskName())
                .append("'. Errors found:");

        formVariables.getErrors().forEach(message -> buffer.append("\n").append(message));
        buffer.append("\n").append("Please check your task definitions.");
        return buffer.toString();
    }

    private ProcessTaskFormsGenerationResult readUserTaskFormVariables(FlowElementsContainer container) {
        ProcessTaskFormsGenerationResult result = new ProcessTaskFormsGenerationResult(container.getId());

        readUserTaskFormVariables(container,
                                  result);

        return result;
    }

    private void readUserTaskFormVariables(FlowElementsContainer container,
                                           ProcessTaskFormsGenerationResult result) {

        container.getFlowElements().stream().filter(flowElement -> flowElement instanceof UserTask).map(flowElement -> (UserTask) flowElement).forEach(userTask -> readTaskVariables(userTask,
                                                                                                                                                                                     result));

        container.getFlowElements().stream().filter(flowElement -> flowElement instanceof FlowElementsContainer).map(flowElement -> (FlowElementsContainer) flowElement).forEach(flowElementsContainer -> readUserTaskFormVariables(flowElementsContainer,
                                                                                                                                                                                                                                    result));
    }

    protected void readTaskVariables(UserTask userTask,
                                     ProcessTaskFormsGenerationResult result) {

        TaskFormVariables formVariables = new TaskFormVariables(userTask);

        List<DataInputAssociation> dataInputAssociations = userTask.getDataInputAssociations();

        if (dataInputAssociations != null) {

            for (DataInputAssociation inputAssociation : dataInputAssociations) {
                if (inputAssociation.getTargetRef() != null) {

                    String name = ((DataInput) inputAssociation.getTargetRef()).getName();

                    if (BPMNVariableUtils.isValidInputName(name)) {

                        String type = Optional.ofNullable(inputAssociation.getTargetRef().getItemSubjectRef())
                                .map(ItemDefinition::getStructureRef)
                                .orElse(inputAssociation.getTargetRef().getAnyAttribute().get(0).getValue().toString());

                        type = BPMNVariableUtils.getRealTypeForInput(type);

                        formVariables.addVariable(name,
                                                  type);
                    } else if (BPMNVariableUtils.TASK_FORM_VARIABLE.equals(name)) {
                        List<Assignment> assignments = inputAssociation.getAssignment();

                        for (Iterator<Assignment> it = assignments.iterator(); it.hasNext() && StringUtils.isEmpty(
                                formVariables.getTaskName()); ) {
                            Assignment assignment = it.next();
                            if (assignment.getFrom() != null) {
                                String taskName = ((FormalExpression) assignment.getFrom()).getBody();
                                if (!StringUtils.isEmpty(taskName)) {
                                    // Parsing taskName... it comes in a <![CDATA[]]>
                                    taskName = Parser.xmlParser().parseInput(taskName,
                                                                             "").toString();
                                    formVariables.setTaskName(taskName);
                                }
                            }
                        }
                    }
                }
            }
        }

        List<DataOutputAssociation> dataOutputAssociations = userTask.getDataOutputAssociations();

        if (dataOutputAssociations != null) {

            dataOutputAssociations.forEach(outputAssociation -> {
                if (outputAssociation.getSourceRef() != null && outputAssociation.getSourceRef().size() == 1) {

                    DataOutput output = (DataOutput) outputAssociation.getSourceRef().get(0);

                    String name = output.getName();

                    String type = Optional.ofNullable(output.getItemSubjectRef())
                            .map(ItemDefinition::getStructureRef)
                            .orElse(output.getAnyAttribute().get(0).getValue().toString());

                    type = BPMNVariableUtils.getRealTypeForInput(type);

                    formVariables.addVariable(name,
                                              type);
                }
            });
        }

        if (!StringUtils.isEmpty(formVariables.getTaskName())) {
            result.registerTaskFormVariables(userTask.getId(),
                                             formVariables);
        } else {
            logger.warn("Cannot generate a form for task '{}' since it has no form name.", userTask.getName());
        }
    }

    protected Process getProcess(Definitions source) {
        for (RootElement re : source.getRootElements()) {
            if (re instanceof Process) {
                return (Process) re;
            }
        }
        return null;
    }
}
