package org.opencds.cqf.r4.processors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.xml.bind.JAXBException;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.ActivityDefinition;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedArtifact;
import org.hl7.fhir.r4.model.RequestGroup;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.TriggerDefinition;
import org.hl7.fhir.r4.model.PlanDefinition.ActionRelationshipType;
import org.hl7.fhir.r4.model.PlanDefinition.PlanDefinitionActionComponent;
import org.hl7.fhir.r4.model.PlanDefinition.PlanDefinitionActionRelatedActionComponent;
import org.opencds.cqf.common.config.HapiProperties;
import org.opencds.cqf.common.exceptions.NotImplementedException;
import org.opencds.cqf.common.helpers.ClientHelper;
import org.opencds.cqf.cql.engine.model.ModelResolver;
import org.opencds.cqf.cql.engine.execution.Context;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.engine.runtime.DateTime;
import org.opencds.cqf.r4.builders.AttachmentBuilder;
import org.opencds.cqf.r4.builders.CarePlanActivityBuilder;
import org.opencds.cqf.r4.builders.CarePlanBuilder;
import org.opencds.cqf.r4.builders.ExtensionBuilder;
import org.opencds.cqf.r4.builders.JavaDateBuilder;
import org.opencds.cqf.r4.builders.ReferenceBuilder;
import org.opencds.cqf.r4.builders.RelatedArtifactBuilder;
import org.opencds.cqf.r4.builders.RequestGroupActionBuilder;
import org.opencds.cqf.r4.builders.RequestGroupBuilder;
import org.opencds.cqf.r4.helpers.CanonicalHelper;
import org.opencds.cqf.r4.providers.PlanDefinitionApplyProvider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.client.api.IGenericClient;

public class PlanDefinitionApplyProcessor {

    private CqlExecutionProcessor executionProcessor;
    private ModelResolver modelResolver;
    private ActivityDefinitionApplyProcessor activityDefinitionApplyProcessor;
    private IGenericClient artifactClient;
    private FhirContext fhirContext;

    public PlanDefinitionApplyProcessor(FhirContext fhirContext,
            ActivityDefinitionApplyProcessor activityDefinitionApplyProcessor, DaoRegistry registry,
            CqlExecutionProcessor executionProcessor) {
        this.executionProcessor = executionProcessor;
        this.modelResolver = new R4FhirModelResolver();
        this.activityDefinitionApplyProcessor = activityDefinitionApplyProcessor;
        this.fhirContext = fhirContext;
    }

    public IGenericClient getArtifactClient() {
        return artifactClient;
    }

    public CarePlan applyPlanDefinition(IdType theId, String patientId, String encounterId, String practitionerId,
            String organizationId, String userType, String userLanguage, String userTaskContext, String setting,
            String settingContext, Endpoint artifactEndpoint) throws IOException, JAXBException, FHIRException {
        this.artifactClient = ClientHelper.getClient(fhirContext, artifactEndpoint);
        PlanDefinition planDefinition = artifactClient.read().resource(PlanDefinition.class).withId(theId).execute();

        if (planDefinition == null) {
            throw new IllegalArgumentException("Couldn't find PlanDefinition " + theId);
        }

        PlanDefinitionApplyProvider.logger.info("Performing $apply operation on PlanDefinition/" + theId);

        CarePlanBuilder builder = new CarePlanBuilder();

        builder.buildInstantiatesCanonical(planDefinition.getIdElement().getIdPart())
                .buildSubject(new Reference("Patient/" + patientId)).buildStatus(CarePlan.CarePlanStatus.DRAFT);

        if (encounterId != null)
            builder.buildEncounter(new Reference(encounterId));
        if (practitionerId != null)
            builder.buildAuthor(new Reference(practitionerId));
        if (organizationId != null)
            builder.buildAuthor(new Reference(organizationId));
        if (userLanguage != null)
            builder.buildLanguage(userLanguage);

        Session session = new Session(planDefinition, builder, patientId, encounterId, practitionerId, organizationId,
                userType, userLanguage, userTaskContext, setting, settingContext);

        session.getCarePlan().setId(new IdType("careplan-" + planDefinition.getIdElement().getIdPart()
                .replace("planDefinition-", "").replace("plandefinition-", "")));

        return resolveActions(session);
    }

    private CarePlan resolveActions(Session session) {
        Map<String, PlanDefinitionActionComponent> metConditions = new HashMap<String, PlanDefinitionActionComponent>();
        for (PlanDefinition.PlanDefinitionActionComponent action : session.getPlanDefinition().getAction()) {
            // TODO - Apply input/output dataRequirements?
            if (meetsConditions(session, action)) {
                if (action.hasRelatedAction()) {
                    for (PlanDefinitionActionRelatedActionComponent relatedActionComponent : action
                            .getRelatedAction()) {
                        if (relatedActionComponent.getRelationship().equals(ActionRelationshipType.AFTER)) {
                            if (metConditions.containsKey(relatedActionComponent.getActionId())) {
                                metConditions.put(action.getId(), action);
                                resolveDefinition(session, action);
                                resolveDynamicActions(session, action);
                            }
                        }
                    }
                } else {
                    metConditions.put(action.getId(), action);
                    resolveDefinition(session, action);
                    resolveDynamicActions(session, action);
                }
            }
        }

        return session.getCarePlan();
    }

    private void resolveDefinition(Session session, PlanDefinition.PlanDefinitionActionComponent action) {
        if (action.hasDefinition()) {
            PlanDefinitionApplyProvider.logger
                    .debug("Resolving definition " + action.getDefinitionCanonicalType().getValue());
            String definition = action.getDefinitionCanonicalType().getValue();
            if (definition.startsWith(session.getPlanDefinition().fhirType())) {
                PlanDefinitionApplyProvider.logger.error("Currently cannot resolve nested PlanDefinitions");
                throw new NotImplementedException(
                        "Plan Definition refers to sub Plan Definition, this is not yet supported");
            }

            else {
                // need to apply action
                Resource result;
                try {
                    if (action.getDefinitionCanonicalType().getValue().startsWith("#")) {
                        result = this.activityDefinitionApplyProcessor.resolveActivityDefinition(
                                (ActivityDefinition) resolveContained(session.getPlanDefinition(),
                                        action.getDefinitionCanonicalType().getValue()),
                                session.getPatientId(), session.getPractionerId(), session.getOrganizationId());
                    } else {
                        result = this.activityDefinitionApplyProcessor.apply(
                                new IdType(CanonicalHelper.getId(action.getDefinitionCanonicalType())),
                                session.getPatientId(), session.getEncounterId(), session.getPractionerId(),
                                session.getOrganizationId(), null, session.getUserLanguage(),
                                session.getUserTaskContext(), session.getSetting(), session.getSettingContext());
                    }

                    if (result.getId() == null) {
                        PlanDefinitionApplyProvider.logger.warn(
                                "ActivityDefinition %s returned resource with no id, setting one",
                                action.getDefinitionCanonicalType().getId());
                        result.setId(UUID.randomUUID().toString());
                    }

                    applyAction(session, result, action);
                    session.getCarePlanBuilder().buildContained(result).buildActivity(new CarePlanActivityBuilder()
                            .buildReference(new Reference("#" + result.getId()).setType(result.fhirType())).build());
                } catch (Exception e) {
                    PlanDefinitionApplyProvider.logger.error(
                            "ERROR: ActivityDefinition %s could not be applied and threw exception %s",
                            action.getDefinition(), e.toString());
                }
            }
        }
    }

    private void applyAction(Session session, Resource result, PlanDefinition.PlanDefinitionActionComponent action) {
        switch (result.fhirType()) {
            case "Task":
                result = resolveTask(session, (Task) result, action);
                break;
        }
    }

    /*
     * offset -> Duration timing -> Timing ( just our use case for connectathon
     * period periodUnit frequency count ) use task code
     */
    private Resource resolveTask(Session session, Task task, PlanDefinitionActionComponent action) {
        if (!task.hasCode()) {
            if(action.hasCode()) {
                for (CodeableConcept actionCode : action.getCode()) {
                    Boolean foundExecutableTaskCode = false;
                    for (Coding actionCoding : actionCode.getCoding()) {
                        if (actionCoding.getSystem().equals("http://aphl.org/fhir/ecr/CodeSystem/executable-task-types")) {
                            foundExecutableTaskCode = true;
                            task.setId(new IdType("task-" + actionCoding.getCode()));
                        }
                    }
                    if (foundExecutableTaskCode) {
                        task.setCode(actionCode);
                    }
                }
            }
        }
        if (action.hasRelatedAction()) {
            List<PlanDefinitionActionRelatedActionComponent> relatedActions = action.getRelatedAction();
            for (PlanDefinitionActionRelatedActionComponent relatedAction : relatedActions) {
                if (relatedAction.hasOffset()) {
                    Extension offsetExtension = new Extension();
                    offsetExtension.setUrl("http://hl7.org/fhir/aphl/StructureDefinition/offset");
                    offsetExtension.setValue(relatedAction.getOffset());
                    task.addExtension(offsetExtension);
                    Reference reference = new Reference();
                    Optional<PlanDefinitionActionComponent> optionalRelatedAction = session.getPlanDefinition().getAction().stream().filter(planDefAction -> planDefAction.getId().equals(relatedAction.getActionId())).findFirst();
                    if (optionalRelatedAction.isPresent()) {
                        PlanDefinition.PlanDefinitionActionComponent relatedActionComponent = optionalRelatedAction.get();
                        String executableUrl = "http://aphl.org/fhir/ecr/CodeSystem/executable-task-types";
                        if(relatedActionComponent.hasCode()) {
                            for (CodeableConcept relatedActionCode : relatedActionComponent.getCode()) {
                                for (Coding relatedActionCoding : relatedActionCode.getCoding()) {
                                    if(task.hasCode()) {
                                        for (Coding taskCoding : task.getCode().getCoding()) {
                                        //only if both actions are executable
                                        if (relatedActionCoding.getSystem().equals(executableUrl) && taskCoding.getSystem().equals(executableUrl)) {
                                            Extension relativePeriodExtension = new Extension();
                                            relativePeriodExtension.setUrl("http://hl7.org/fhir/extension-cqf-relativedatetime.html");
                                            reference.setReference("task-" + relatedActionCoding.getCode());
                                            reference.setType("Task");
                                            relativePeriodExtension.setValue(reference);
                                            task.getExecutionPeriod().addExtension(relativePeriodExtension);
                                            break;
                                        } else if (taskCoding.getSystem().equals(executableUrl)) {
                                            //offset needs to be applied to Encounter start
                                        }
                                    }
                                }
                            }
                        }
                    }
                        
                    }
                }
            }
        }
        if (action.hasTiming()) {
            Extension timingExtension = new Extension();
            timingExtension.setUrl("http://hl7.org/fhir/aphl/StructureDefinition/timing");
            timingExtension.setValue(action.getTiming());
            task.addExtension(timingExtension);
        }
        if (action.hasTrigger()) {
            List<TriggerDefinition> triggers = action.getTrigger();
            for (TriggerDefinition trigger : triggers) {
                if (trigger.hasTiming()) {
                    Extension timingExtension = new Extension();
                    timingExtension.setUrl("http://hl7.org/fhir/aphl/StructureDefinition/timing");
                    timingExtension.setValue(trigger.getTiming());
                    task.addExtension(timingExtension);
                }
            }
        }
        task.addBasedOn(new Reference(session.getCarePlan()).setType(session.getCarePlan().fhirType()).setReference(session.getCarePlan().getId()));
        return task;
    }

    private void resolveDynamicActions(Session session, PlanDefinition.PlanDefinitionActionComponent action) {
        for (PlanDefinition.PlanDefinitionActionDynamicValueComponent dynamicValue : action.getDynamicValue()) {
            PlanDefinitionApplyProvider.logger.info("Resolving dynamic value %s %s", dynamicValue.getPath(),
                    dynamicValue.getExpression());
            if (dynamicValue.hasExpression()) {
                Object result = executionProcessor.evaluateInContext(session.getPlanDefinition(),
                        dynamicValue.getExpression().getExpression(), session.getPatientId());

                if (dynamicValue.hasPath() && dynamicValue.getPath().equals("$this")) {
                    session.setCarePlan((CarePlan) result);
                }

                else {

                    // TODO - likely need more date tranformations
                    if (result instanceof DateTime) {
                        result = new JavaDateBuilder().buildFromDateTime((DateTime) result).build();
                    }

                    else if (result instanceof String) {
                        result = new StringType((String) result);
                    }

                    this.modelResolver.setValue(session.getCarePlan(), dynamicValue.getPath(), result);
                }
            }
        }
    }

    private Boolean meetsConditions(Session session, PlanDefinition.PlanDefinitionActionComponent action) {
        if (action.hasAction()) {
            for (PlanDefinition.PlanDefinitionActionComponent containedAction : action.getAction()) {
                meetsConditions(session, containedAction);
            }
        }
        for (PlanDefinition.PlanDefinitionActionConditionComponent condition : action.getCondition()) {
            // TODO start
            // TODO stop
            if (condition.hasExpression() && condition.getExpression().hasDescription()) {
                PlanDefinitionApplyProvider.logger
                        .info("Resolving condition with description: " + condition.getExpression().getDescription());
            }
            if (condition.getKind() == PlanDefinition.ActionConditionKind.APPLICABILITY) {
                if (condition.hasExpression() && condition.getExpression().hasLanguage()
                        && !condition.getExpression().getLanguage().equals("text/cql")
                        && !condition.getExpression().getLanguage().equals("text/cql.name")) {
                    PlanDefinitionApplyProvider.logger.warn(
                            "An action language other than CQL was found: " + condition.getExpression().getLanguage());
                    continue;
                }

                if (!condition.hasExpression()) {
                    PlanDefinitionApplyProvider.logger.error("Missing condition expression");
                    throw new RuntimeException("Missing condition expression");
                }

                PlanDefinitionApplyProvider.logger
                        .info("Evaluating action condition expression " + condition.getExpression());
                String cql = condition.getExpression().getExpression();
                String language = condition.getExpression().getLanguage();
                Object result = null;
                result = (language.equals("text/cql.name"))
                        ? executionProcessor.evaluateInContext(session.getPlanDefinition(), cql, session.getPatientId(),
                                true)
                        : executionProcessor.evaluateInContext(session.getPlanDefinition(), cql,
                                session.getPatientId());

                if (result == null) {
                    PlanDefinitionApplyProvider.logger.warn("Expression Returned null");
                    return false;
                }

                if (!(result instanceof Boolean)) {
                    PlanDefinitionApplyProvider.logger
                            .warn("The condition returned a non-boolean value: " + result.getClass().getSimpleName());
                    continue;
                }

                if (!(Boolean) result) {
                    PlanDefinitionApplyProvider.logger.info("The result of condition expression %s is false",
                            condition.getExpression());
                    return false;
                }
            }
        }

        return true;
    }

    // For library use
    public CarePlan resolveCdsHooksPlanDefinition(Context context, PlanDefinition planDefinition, String patientId) {

        CarePlanBuilder carePlanBuilder = new CarePlanBuilder();
        RequestGroupBuilder requestGroupBuilder = new RequestGroupBuilder().buildStatus().buildIntent();

        // links
        if (planDefinition.hasRelatedArtifact()) {
            List<Extension> extensions = new ArrayList<>();
            for (RelatedArtifact relatedArtifact : planDefinition.getRelatedArtifact()) {
                AttachmentBuilder attachmentBuilder = new AttachmentBuilder();
                ExtensionBuilder extensionBuilder = new ExtensionBuilder();
                if (relatedArtifact.hasDisplay()) { // label
                    attachmentBuilder.buildTitle(relatedArtifact.getDisplay());
                }
                if (relatedArtifact.hasUrl()) { // url
                    attachmentBuilder.buildUrl(relatedArtifact.getUrl());
                }
                if (relatedArtifact.hasExtension()) { // type
                    attachmentBuilder.buildExtension(relatedArtifact.getExtension());
                }
                extensionBuilder.buildUrl("http://example.org");
                extensionBuilder.buildValue(attachmentBuilder.build());
                extensions.add(extensionBuilder.build());
            }
            requestGroupBuilder.buildExtension(extensions);
        }

        resolveActions(planDefinition.getAction(), context, patientId, requestGroupBuilder, new ArrayList<>());

        CarePlanActivityBuilder carePlanActivityBuilder = new CarePlanActivityBuilder();
        carePlanActivityBuilder.buildReferenceTarget(requestGroupBuilder.build());
        carePlanBuilder.buildActivity(carePlanActivityBuilder.build());

        return carePlanBuilder.build();
    }

    private void resolveActions(List<PlanDefinition.PlanDefinitionActionComponent> actions, Context context,
            String patientId, RequestGroupBuilder requestGroupBuilder,
            List<RequestGroup.RequestGroupActionComponent> actionComponents) {
        for (PlanDefinition.PlanDefinitionActionComponent action : actions) {
            boolean conditionsMet = true;
            for (PlanDefinition.PlanDefinitionActionConditionComponent condition : action.getCondition()) {
                if (condition.getKind() == PlanDefinition.ActionConditionKind.APPLICABILITY) {
                    if (!condition.hasExpression()) {
                        continue;
                    }

                    if (condition.hasExpression() && !condition.getExpression().hasExpression()) {
                        continue;
                    }

                    Object result = context.resolveExpressionRef(condition.getExpression().getExpression())
                            .getExpression().evaluate(context);

                    if (!(result instanceof Boolean)) {
                        continue;
                    }

                    if (!(Boolean) result) {
                        conditionsMet = false;
                    }
                }

                if (conditionsMet) {
                    RequestGroupActionBuilder actionBuilder = new RequestGroupActionBuilder();
                    if (action.hasTitle()) {
                        actionBuilder.buildTitle(action.getTitle());
                    }
                    if (action.hasDescription()) {
                        actionBuilder.buildDescripition(action.getDescription());
                    }

                    // source
                    if (action.hasDocumentation()) {
                        RelatedArtifact artifact = action.getDocumentationFirstRep();
                        RelatedArtifactBuilder artifactBuilder = new RelatedArtifactBuilder();
                        if (artifact.hasDisplay()) {
                            artifactBuilder.buildDisplay(artifact.getDisplay());
                        }
                        if (artifact.hasUrl()) {
                            artifactBuilder.buildUrl(artifact.getUrl());
                        }
                        if (artifact.hasDocument() && artifact.getDocument().hasUrl()) {
                            AttachmentBuilder attachmentBuilder = new AttachmentBuilder();
                            attachmentBuilder.buildUrl(artifact.getDocument().getUrl());
                            artifactBuilder.buildDocument(attachmentBuilder.build());
                        }
                        actionBuilder.buildDocumentation(Collections.singletonList(artifactBuilder.build()));
                    }

                    // suggestions
                    // TODO - uuid
                    if (action.hasPrefix()) {
                        actionBuilder.buildPrefix(action.getPrefix());
                    }
                    if (action.hasType()) {
                        actionBuilder.buildType(action.getType());
                    }
                    if (action.hasDefinition()) {
                        if (action.getDefinitionCanonicalType().getValue().contains("ActivityDefinition")) {
                            ActivityDefinition activityDefinition = artifactClient.read()
                                    .resource(ActivityDefinition.class).withId(new IdType("ActivityDefinition",
                                            action.getDefinitionCanonicalType().getId()))
                                    .execute();
                            if (activityDefinition.hasDescription()) {
                                actionBuilder.buildDescripition(activityDefinition.getDescription());
                            }
                            Resource resource;
                            try {
                                resource = this.activityDefinitionApplyProcessor
                                        .apply(new IdType(action.getDefinitionCanonicalType().getId()), patientId, null,
                                                null, null, null, null, null, null, null)
                                        .setId(UUID.randomUUID().toString());
                            } catch (FHIRException | ClassNotFoundException | InstantiationException
                                    | IllegalAccessException e) {
                                throw new RuntimeException("Error applying ActivityDefinition " + e.getMessage());
                            }

                            Parameters inParams = new Parameters();
                            inParams.addParameter().setName("patient").setValue(new StringType(patientId));
                            Parameters outParams = this.fhirContext
                                    .newRestfulGenericClient(HapiProperties.getServerBase()).operation()
                                    .onInstance(new IdDt("ActivityDefinition", action.getDefinition().getId()))
                                    .named("$apply").withParameters(inParams).useHttpGet().execute();

                            List<Parameters.ParametersParameterComponent> response = outParams.getParameter();
                            resource = response.get(0).getResource().setId(UUID.randomUUID().toString());
                            actionBuilder.buildResourceTarget(resource);
                            actionBuilder
                                    .buildResource(new ReferenceBuilder().buildReference(resource.getId()).build());
                        }
                    }

                    // Dynamic values populate the RequestGroup - there is a bit of hijacking going
                    // on here...
                    if (action.hasDynamicValue()) {
                        for (PlanDefinition.PlanDefinitionActionDynamicValueComponent dynamicValue : action
                                .getDynamicValue()) {
                            if (dynamicValue.hasPath() && dynamicValue.hasExpression()) {
                                if (dynamicValue.getPath().endsWith("title")) { // summary
                                    String title = (String) context
                                            .resolveExpressionRef(dynamicValue.getExpression().getExpression())
                                            .evaluate(context);
                                    actionBuilder.buildTitle(title);
                                } else if (dynamicValue.getPath().endsWith("description")) { // detail
                                    String description = (String) context
                                            .resolveExpressionRef(dynamicValue.getExpression().getExpression())
                                            .evaluate(context);
                                    actionBuilder.buildDescripition(description);
                                } else if (dynamicValue.getPath().endsWith("extension")) { // indicator
                                    String extension = (String) context
                                            .resolveExpressionRef(dynamicValue.getExpression().getExpression())
                                            .evaluate(context);
                                    actionBuilder.buildExtension(extension);
                                }
                            }
                        }
                    }

                    if (!actionBuilder.build().isEmpty()) {
                        actionComponents.add(actionBuilder.build());
                    }

                    if (action.hasAction()) {
                        resolveActions(action.getAction(), context, patientId, requestGroupBuilder, actionComponents);
                    }
                }
            }
        }
        requestGroupBuilder.buildAction(new ArrayList<>(actionComponents));
    }

    public Resource resolveContained(DomainResource resource, String id) {
        for (Resource res : resource.getContained()) {
            if (res.hasIdElement()) {
                if (res.getIdElement().getIdPart().equals(id)) {
                    return res;
                }
            }
        }

        throw new RuntimeException(
                String.format("Resource %s does not contain resource with id %s", resource.fhirType(), id));
    }

}

class Session {
    private final String patientId;
    private final PlanDefinition planDefinition;
    private final String practionerId;
    private final String organizationId;
    private final String userType;
    private final String userLanguage;
    private final String userTaskContext;
    private final String setting;
    private final String settingContext;
    private CarePlanBuilder carePlanBuilder;
    private String encounterId;

    public Session(PlanDefinition planDefinition, CarePlanBuilder builder, String patientId, String encounterId,
            String practitionerId, String organizationId, String userType, String userLanguage, String userTaskContext,
            String setting, String settingContext) {
        this.patientId = patientId;
        this.planDefinition = planDefinition;
        this.carePlanBuilder = builder;
        this.encounterId = encounterId;
        this.practionerId = practitionerId;
        this.organizationId = organizationId;
        this.userType = userType;
        this.userLanguage = userLanguage;
        this.userTaskContext = userTaskContext;
        this.setting = setting;
        this.settingContext = settingContext;
    }

    public PlanDefinition getPlanDefinition() {
        return this.planDefinition;
    }

    public String getPatientId() {
        return patientId;
    }

    public CarePlan getCarePlan() {
        return carePlanBuilder.build();
    }

    public void setCarePlan(CarePlan carePlan) {
        this.carePlanBuilder = new CarePlanBuilder(carePlan);
    }

    public String getEncounterId() {
        return this.encounterId;
    }

    public String getPractionerId() {
        return practionerId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getUserType() {
        return userType;
    }

    public String getUserLanguage() {
        return userLanguage;
    }

    public String getUserTaskContext() {
        return userTaskContext;
    }

    public String getSetting() {
        return setting;
    }

    public String getSettingContext() {
        return settingContext;
    }

    public CarePlanBuilder getCarePlanBuilder() {
        return carePlanBuilder;
    }
}
