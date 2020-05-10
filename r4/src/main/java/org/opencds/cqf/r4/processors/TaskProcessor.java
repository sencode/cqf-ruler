package org.opencds.cqf.r4.processors;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.jpa.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.client.api.IGenericClient;

import org.hl7.fhir.r4.model.Task.TaskOutputComponent;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.*;

import java.util.LinkedList;
import java.util.List;

import org.hl7.fhir.r4.model.Task.TaskStatus;
import org.hl7.fhir.r4.model.CarePlan.CarePlanStatus;
import org.opencds.cqf.common.helpers.ClientHelper;
import org.opencds.cqf.r4.execution.ITaskProcessor;
import org.opencds.cqf.r4.managers.ERSDTaskManager;

public class TaskProcessor implements ITaskProcessor<Task> {

    private FhirContext fhirContext;
    private IGenericClient workFlowClient;
    private IGenericClient localClient;

    public TaskProcessor(FhirContext fhirContext, IGenericClient localClient, IGenericClient workFlowClient) {
        this.fhirContext = fhirContext;
        this.workFlowClient = workFlowClient;
        this.localClient = localClient;
    }

    public IAnyResource execute(Task task, Endpoint endpoint) {
        workFlowClient = ClientHelper.getClient(fhirContext, endpoint);
        return execute(task);
    }

    public IAnyResource execute(Task task) {
        workFlowClient.read().resource(Task.class).withId(task.getIdElement()).execute();
        ERSDTaskManager ersdTaskManager = new ERSDTaskManager(fhirContext, localClient, workFlowClient);
        String taskId = task.getIdElement().getIdPart();
        IAnyResource result = null;
        try {
            result = ersdTaskManager.forTask(task);
        } catch (InstantiationException e) {
            System.out.println("unable to execute Task: " + taskId);
            e.printStackTrace();
        }
        resolveStatusAndUpdate(task, result);
        return result;   
    }

    private void resolveStatusAndUpdate(Task task, IAnyResource executionResult) {
        //create extension countExecuted to determine completed
        //or use Timing count compared to event *Ask Bryn whether event is a record or directive*
        //task.setStatus(TaskStatus.COMPLETED);
        TaskOutputComponent taskOutputComponent = new TaskOutputComponent();
        CodeableConcept typeCodeableConcept = new CodeableConcept();
        taskOutputComponent.setType(typeCodeableConcept);
        Reference resultReference = new Reference();
        resultReference.setType(executionResult.fhirType());
        resultReference.setReference(executionResult.getId());
        taskOutputComponent.setValue(resultReference);
        task.addOutput(taskOutputComponent);
        workFlowClient.update().resource(task).execute();
        List<Reference> basedOnReferences = task.getBasedOn();
        if (basedOnReferences.isEmpty() || basedOnReferences == null) {
            throw new RuntimeException("Task must fullfill a request in order to be applied. i.e. must have a basedOn element containing a reference to a Resource");
        }
        List<CarePlan> carePlansAssociatedWithTask = new LinkedList<CarePlan>();
        basedOnReferences.stream()
            .filter(reference -> reference.getReference().contains("CarePlan/"))
            .map(reference -> workFlowClient.read().resource(CarePlan.class).withId(reference.getReference()).execute())
            .forEach(carePlan -> carePlansAssociatedWithTask.add((CarePlan)carePlan));
        
        if (basedOnReferences.isEmpty()) {
            throw new RuntimeException("$taskApply only supports tasks based on CarePlans as of now.");  
        }

        for (CarePlan carePlan : carePlansAssociatedWithTask) {
            List<Task> carePlanTasks = new LinkedList<Task>();
            carePlan.getContained().stream()
                .filter(resource -> (resource instanceof Task))
                .map(resource -> (Task)resource)
                .forEach(containedTask -> carePlanTasks.add(containedTask));
            boolean allTasksCompleted = true;
            for (Task containedTask : carePlanTasks) {
                containedTask.setId(containedTask.getIdElement().getIdPart().replaceAll("#", ""));
                containedTask = workFlowClient.read().resource(Task.class).withId(containedTask.getId()).execute();
                if(containedTask.getStatus() != TaskStatus.COMPLETED) {
                    allTasksCompleted = false;
                }
            }
            if(allTasksCompleted) {
                carePlan.setStatus(CarePlanStatus.COMPLETED);
                workFlowClient.update().resource(carePlan).execute();
            }
        }
        workFlowClient.update().resource(task).execute();
    }

}