package org.opencds.cqf.r4.processors;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;

import org.hl7.fhir.r4.model.Task.TaskOutputComponent;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.*;

import java.util.LinkedList;
import java.util.List;

import org.hl7.fhir.r4.model.Task.TaskStatus;
import org.hl7.fhir.r4.model.CarePlan.CarePlanActivityComponent;
import org.hl7.fhir.r4.model.CarePlan.CarePlanStatus;
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
        workFlowClient.update().resource(task).execute();
        updateCarePlanTasks(task);
        workFlowClient.update().resource(task).execute();
    }

    private void updateCarePlanTasks(Task task) {
        List<Reference> basedOnReferences = task.getBasedOn();
        if (basedOnReferences.isEmpty() || basedOnReferences == null) {
            throw new RuntimeException("Task must fullfill a request in order to be applied. i.e. must have a basedOn element containing a reference to a Resource");
        }
        if (basedOnReferences.isEmpty()) {
            throw new RuntimeException("$taskApply only supports tasks based on CarePlans as of now.");  
        }
        CarePlan carePlan = null;
        for (Reference reference : task.getBasedOn()) {
            if (reference.hasType() && reference.getType().equals("CarePlan")) {
                carePlan = workFlowClient.read().resource(CarePlan.class).withId(reference.getReference()).execute();
                List<Resource> carePlanTasks = new LinkedList<Resource>();
            carePlan.getContained().stream()
                .filter(resource -> (resource instanceof Task))
                .map(resource -> (Task)resource)
                .forEach(containedTask -> {
                    String containedTaskId = containedTask.getIdElement().getIdPart().replaceAll("#", "");
                    containedTask.setId(containedTaskId);
                    containedTask = workFlowClient.read().resource(Task.class).withId(containedTask.getId()).execute();
                    carePlanTasks.add(containedTask.setId(containedTaskId));
                });
                boolean allTasksCompleted = true;
                for (Resource containedTask : carePlanTasks) {
                    Task containedTaskResource = (Task)containedTask;
                    if(containedTaskResource.getStatus() != TaskStatus.COMPLETED) {
                        allTasksCompleted = false;
                    }
                    //This is necessary as of now because the Task Resource is tacked on behind the scenes when referencing a contained Resource.
                    if (carePlan.hasActivity()) {
                        for (CarePlanActivityComponent activity : carePlan.getActivity()) {
                            if (activity.getReference().getReference().equals("#" + containedTask.getIdElement().getIdPart())) {
                                activity.setReference(new Reference("#" + containedTask.getId()).setType(containedTask.fhirType()));
                            }
                        }
                    }
                }
                
            if(allTasksCompleted) {
                carePlan.setStatus(CarePlanStatus.COMPLETED);
            }
            carePlan.setContained(carePlanTasks);
            //hack for now get latest
            CarePlan oldCarePlan = workFlowClient.read().resource(CarePlan.class).withId(carePlan.getIdElement().getIdPart()).execute();
            carePlan.setId(oldCarePlan.getIdElement());
            workFlowClient.update().resource(carePlan).execute();
            }
        }            
        }

    @Override
    public void update(Task task) {
        workFlowClient.update().resource(task).execute();
        updateCarePlanTasks(task);
    }

}