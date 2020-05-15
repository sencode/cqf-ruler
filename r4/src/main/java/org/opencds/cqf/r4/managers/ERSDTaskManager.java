package org.opencds.cqf.r4.managers;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Task.TaskOutputComponent;
import org.hl7.fhir.r4.model.Task.TaskStatus;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.context.FhirContext;

public class ERSDTaskManager {

    private IGenericClient workFlowClient;
    private IGenericClient localClient;

    public ERSDTaskManager(FhirContext fhirContext, IGenericClient localClient, IGenericClient workFlowClient) {
        this.localClient = localClient;
        this.workFlowClient = workFlowClient;
    }

    public Resource forTask(Task task) throws InstantiationException {
        Resource resource = null;
        // Cant use taskCode
        for (Coding coding : task.getCode().getCoding()) {
            switch (coding.getCode()) {
                // these need to be codes
                case ("rulefilter-report"):
                    resource = task;
                    break;
                case ("create-eicr"):
                    resource = executeCreateEICR(task);
                    System.out.println(task.getId() + " executed");
                    break;
                case ("periodic-update-eicr"):
                    resource = updateEICR(task);
                    System.out.println(task.getId() + " executed");
                    break;
                case ("close-out-eicr"):
                    resource = closeOutEICR(task);
                    System.out.println(task.getId() + " executed");
                    break;
                case ("validate-eicr"):
                    resource = validateEicr(task);
                    System.out.println(task.getId() + " executed");
                    break;
                case ("route-and-send-eicr"):
                    resource = routeAndSend(task);
                    System.out.println(task.getId() + " executed");
                    break;
                default:
                    throw new InstantiationException("Unknown task Apply type.");
            }
        }
        return resource;
    }

    // Demo get $notify -> PlanDef$apply -> executeCarePlan -> eventually posts eicr
    // to fhir endpoint
    private Bundle routeAndSend(Task task) {
        CarePlan carePlan = null;
        Bundle eicr = new Bundle();
        for (Reference reference : task.getBasedOn()) {
            if (reference.hasType() && reference.getType().equals("CarePlan")) {
                List<Reference> outComeRefs = new ArrayList<Reference>();
                carePlan = workFlowClient.read().resource(CarePlan.class).withId(reference.getReference()).execute();
                carePlan.getActivity().stream()
                    .filter(activity -> activity.getReference().getReference().equals("#" + "task-create-eicr"))
                    .forEach(activity ->  outComeRefs.addAll(activity.getOutcomeReference()));
                for (Reference ref : outComeRefs) {
                    if (ref.getType().equals("Bundle")) {
                        if (ref.getReference().toLowerCase().contains("eicr")) {
                            eicr = localClient.read().resource(Bundle.class).withId(new IdType(ref.getReference())).execute();
                            IBaseResource response = workFlowClient.update().resource(eicr).execute().getResource();
                            System.out.println("eiCR Routed and Sent.");
                            if (response.fhirType().equals("Bundle")) {
                                eicr = (Bundle)response;
                            }
                        }
                    }
                }
            }
        }
        return eicr;
    }

    private Bundle validateEicr(Task task) {
        CarePlan carePlan = null;
        Bundle eicr = new Bundle();
        for (Reference reference : task.getBasedOn()) {
            if (reference.hasType() && reference.getType().equals("CarePlan")) {
                List<Reference> outComeRefs = new ArrayList<Reference>();
                carePlan = workFlowClient.read().resource(CarePlan.class).withId(reference.getReference()).execute();
                carePlan.getActivity().stream()
                    .filter(activity -> activity.getReference().getReference().equals("#" + "task-create-eicr"))
                    .forEach(activity ->  outComeRefs.addAll(activity.getOutcomeReference()));
                for (Reference ref : outComeRefs) {
                    if (ref.getType().equals("Bundle")) {
                        if (ref.getReference().toLowerCase().contains("eicr")) {
                            eicr = workFlowClient.read().resource(Bundle.class).withId(new IdType(ref.getReference())).execute();
                            workFlowClient.update().resource(eicr).execute().getResource();
                            System.out.println("eicr Validated");
                        }
                    }
                }
            }
        }
        return eicr;
    }

    private Bundle executeCreateEICR(Task task) {
        // find activity #create-eicr
        // set outcome reference on careplan
        Bundle bundle = new Bundle();
        bundle = localClient.read().resource(Bundle.class).withId(new IdType("bundle-eicr-document-zika")).execute();
        System.out.println("eiCR Created.");
        TaskOutputComponent taskOutputComponent = new TaskOutputComponent();
        CodeableConcept typeCodeableConcept = new CodeableConcept();
        taskOutputComponent.setType(typeCodeableConcept);
        Reference resultReference = new Reference();
        resultReference.setType(bundle.fhirType());
        resultReference.setReference(bundle.getId());
        taskOutputComponent.setValue(resultReference);
        task.addOutput(taskOutputComponent);
        Reference bundleReference = new Reference();
        bundleReference.setType("Bundle");
        bundleReference.setReference("Bundle/" + bundle.getIdElement().getIdPart());
        CarePlan carePlan = null;
        for (Reference reference : task.getBasedOn()) {
            if (reference.hasType() && reference.getType().equals("CarePlan")) {
                carePlan = workFlowClient.read().resource(CarePlan.class).withId(reference.getReference()).execute();
                carePlan.getActivity().stream()
                    .filter(activity -> activity.getReference().getReference().equals("#" + task.getIdElement().getIdPart()))
                    .forEach(activity ->  activity.addOutcomeReference(bundleReference));
            }
        }
        //hack for now get latest
        CarePlan oldCarePlan = workFlowClient.read().resource(CarePlan.class).withId(carePlan.getIdElement().getIdPart()).execute();
        carePlan.setId(oldCarePlan.getIdElement());
        workFlowClient.update().resource(carePlan).execute();
        workFlowClient.update().resource(bundle).execute();
        return bundle;
    }

    private Bundle updateEICR(Task task) {
        CarePlan carePlan = null;
        Bundle eicr = new Bundle();
        for (Reference reference : task.getBasedOn()) {
            if (reference.hasType() && reference.getType().equals("CarePlan")) {
                List<Reference> outComeRefs = new ArrayList<Reference>();
                carePlan = workFlowClient.read().resource(CarePlan.class).withId(reference.getReference()).execute();
                carePlan.getActivity().stream()
                    .filter(activity -> activity.getReference().getReference().equals("#" + "task-create-eicr"))
                    .forEach(activity ->  outComeRefs.addAll(activity.getOutcomeReference()));
                for (Reference ref : outComeRefs) {
                    if (ref.getType().equals("Bundle")) {
                        if (ref.getReference().toLowerCase().contains("eicr")) {
                            eicr = workFlowClient.read().resource(Bundle.class).withId(new IdType(ref.getReference())).execute();
                            //TODO: take this out it is for demo purposes only...
                            eicr.addEntry(new BundleEntryComponent());
                            IBaseResource response = workFlowClient.update().resource(eicr).execute().getResource();
                            System.out.println("eiCR Updated.");
                            if (response.fhirType().equals("Bundle")) {
                                eicr = (Bundle)response;
                            }
                        }
                    }
                }
            }
        }
        return eicr;
    }

    private Resource closeOutEICR(Task task) {
        CarePlan carePlan = null;
        Bundle eicr = new Bundle();
        for (Reference reference : task.getBasedOn()) {
            if (reference.hasType() && reference.getType().equals("CarePlan")) {
                List<Reference> outComeRefs = new ArrayList<Reference>();
                carePlan = workFlowClient.read().resource(CarePlan.class).withId(reference.getReference()).execute();
                carePlan.getActivity().stream()
                    .filter(activity -> activity.getReference().getReference().equals("#" + "task-create-eicr"))
                    .forEach(activity ->  outComeRefs.addAll(activity.getOutcomeReference()));
                for (Reference ref : outComeRefs) {
                    if (ref.getType().equals("Bundle")) {
                        if (ref.getReference().toLowerCase().contains("eicr")) {
                            eicr = workFlowClient.read().resource(Bundle.class).withId(new IdType(ref.getReference())).execute();
                            workFlowClient.update().resource(eicr).execute().getResource();
                            System.out.println("eicr closed out.");
                        }
                    }
                }
            }
        }
        return eicr;
    }
}