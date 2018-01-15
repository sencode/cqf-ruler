package org.opencds.cqf.providers;

import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import ca.uhn.fhir.jpa.provider.dstu3.JpaResourceProviderDstu3;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.opencds.cqf.builders.*;
import org.opencds.cqf.cds.CdsHooksRequest;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.runtime.DateTime;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.util.*;

public class FHIRPlanDefinitionResourceProvider extends JpaResourceProviderDstu3<PlanDefinition> {

    private JpaDataProvider provider;
    private CqlExecutionProvider executionProvider;

    public FHIRPlanDefinitionResourceProvider(Collection<IResourceProvider> providers) {
        this.provider = new JpaDataProvider(providers);
        this.executionProvider = new CqlExecutionProvider(providers);
    }

    @Operation(name = "$apply", idempotent = true)
    public CarePlan applyPlanDefinition(
            @IdParam IdType theId,
            @RequiredParam(name="patient") String patientId,
            @OptionalParam(name="encounter") String encounterId,
            @OptionalParam(name="practitioner") String practitionerId,
            @OptionalParam(name="organization") String organizationId,
            @OptionalParam(name="userType") String userType,
            @OptionalParam(name="userLanguage") String userLanguage,
            @OptionalParam(name="userTaskContext") String userTaskContext,
            @OptionalParam(name="setting") String setting,
            @OptionalParam(name="settingContext") String settingContext)
            throws IOException, JAXBException, FHIRException
    {
        PlanDefinition planDefinition = this.getDao().read(theId);

        if (planDefinition == null) {
            throw new IllegalArgumentException("Couldn't find PlanDefintion " + theId);
        }

        CarePlanBuilder builder = new CarePlanBuilder();

        builder
                .buildDefinition(new Reference(planDefinition.getIdElement().getIdPart()))
                .buildSubject(new Reference(patientId))
                .buildStatus(CarePlan.CarePlanStatus.DRAFT);

        if (encounterId != null) builder.buildContext(new Reference(encounterId));
        if (practitionerId != null) builder.buildAuthor(new Reference(practitionerId));
        if (organizationId != null) builder.buildAuthor(new Reference(organizationId));
        if (userLanguage != null) builder.buildLanguage(userLanguage);

        return resolveActions(planDefinition, builder, patientId);
    }

    private CarePlan resolveActions(PlanDefinition planDefinition, CarePlanBuilder builder,
                                        String patientId) throws FHIRException
    {
        for (PlanDefinition.PlanDefinitionActionComponent action : planDefinition.getAction())
        {
            // TODO - Apply input/output dataRequirements?

            if (meetsConditions(planDefinition, patientId, action)) {
                return resolveDynamicValues(planDefinition, builder.build(), patientId, action);
            }
        }

        return builder.build();
    }

    public Boolean meetsConditions(PlanDefinition planDefinition, String patientId,
                                        PlanDefinition.PlanDefinitionActionComponent action)
    {
        for (PlanDefinition.PlanDefinitionActionConditionComponent condition: action.getCondition()) {
            // TODO start
            // TODO stop
            if (condition.getKind() == PlanDefinition.ActionConditionKind.APPLICABILITY) {
                if (!condition.getLanguage().equals("text/cql")) {
                    // TODO - log this
                    continue;
                }

                if (!condition.hasExpression()) {
                    // TODO - log this
                    continue;
                }

                String cql = condition.getExpression();
                Object result = executionProvider.evaluateInContext(planDefinition, cql, patientId);

                if (!(result instanceof Boolean)) {
                    // TODO - log this
                    // maybe try an int value check (i.e. 0 or 1)?
                    continue;
                }

                if (!(Boolean) result) {
                    return false;
                }
            }
        }

        return true;
    }

    private CarePlan resolveDynamicValues(PlanDefinition planDefinition, CarePlan carePlan, String patientId,
                                          PlanDefinition.PlanDefinitionActionComponent action) throws FHIRException
    {
        for (PlanDefinition.PlanDefinitionActionDynamicValueComponent dynamicValue: action.getDynamicValue())
        {
            if (dynamicValue.hasExpression()) {
                Object result =
                        executionProvider
                                .evaluateInContext(planDefinition, dynamicValue.getExpression(), patientId);

                if (dynamicValue.hasPath() && dynamicValue.getPath().equals("$this"))
                {
                    carePlan = (CarePlan) result;
                }

                else {

                    // TODO - likely need more date tranformations
                    if (result instanceof DateTime) {
                        result =
                                new JavaDateBuilder()
                                        .buildFromDateTime((DateTime) result)
                                        .build();
                    }

                    else if (result instanceof String) {
                        result = new StringType((String) result);
                    }

                    provider.setValue(carePlan, dynamicValue.getPath(), result);
                }
            }
        }

        return carePlan;
    }

    public CarePlan resolveCdsHooksPlanDefinition(Context context, CdsHooksRequest request) {
        PlanDefinition planDefinition = this.getDao().read(new IdType(request.getService()));
        return resolveCdsHooksPlanDefinition(context, planDefinition, request.getPatientId());
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
                                List<RequestGroup.RequestGroupActionComponent> actionComponents)
    {
        for (PlanDefinition.PlanDefinitionActionComponent action : actions) {
            boolean conditionsMet = true;
            for (PlanDefinition.PlanDefinitionActionConditionComponent condition: action.getCondition()) {
                if (condition.getKind() == PlanDefinition.ActionConditionKind.APPLICABILITY) {
                    if (!condition.hasExpression()) {
                        continue;
                    }

                    Object result = context.resolveExpressionRef(condition.getExpression()).getExpression().evaluate(context);

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
                    if (action.hasLabel()) {
                        actionBuilder.buildLabel(action.getLabel());
                    }
                    if (action.hasType()) {
                        actionBuilder.buildType(action.getType());
                    }
                    if (action.hasDefinition()) {
                        if (action.getDefinition().getReferenceElement().getResourceType().equals("ActivityDefinition")) {
                            if (action.getDefinition().getResource() != null) {
                                ReferenceBuilder referenceBuilder = new ReferenceBuilder();
                                referenceBuilder.buildDisplay(((ActivityDefinition) action.getDefinition().getResource()).getDescription());
                                actionBuilder.buildResource(referenceBuilder.build());
                            }

                            BaseFhirDataProvider provider = (BaseFhirDataProvider) context.resolveDataProvider(new QName("http://hl7.org/fhir", ""));
                            Parameters inParams = new Parameters();
                            inParams.addParameter().setName("patient").setValue(new StringType(patientId));
                            Parameters outParams = provider.getFhirClient()
                                    .operation()
                                    .onInstance(new IdDt("ActivityDefinition", action.getDefinition().getReferenceElement().getIdPart()))
                                    .named("$apply")
                                    .withParameters(inParams)
                                    .useHttpGet()
                                    .execute();

                            List<Parameters.ParametersParameterComponent> response = outParams.getParameter();
                            Resource resource = response.get(0).getResource().setId(UUID.randomUUID().toString());
                            actionBuilder.buildResourceTarget(resource);
                            actionBuilder.buildResource(new ReferenceBuilder().buildReference(resource.getId()).build());
                        }
                    }

                    // Dynamic values populate the RequestGroup - there is a bit of hijacking going on here...
                    if (action.hasDynamicValue()) {
                        for (PlanDefinition.PlanDefinitionActionDynamicValueComponent dynamicValue : action.getDynamicValue()) {
                            if (dynamicValue.hasPath() && dynamicValue.hasExpression()) {
                                if (dynamicValue.getPath().endsWith("title")) { // summary
                                    String title = (String) context.resolveExpressionRef(dynamicValue.getExpression()).evaluate(context);
                                    actionBuilder.buildTitle(title);
                                }
                                else if (dynamicValue.getPath().endsWith("description")) { // detail
                                    String description = (String) context.resolveExpressionRef(dynamicValue.getExpression()).evaluate(context);
                                    actionBuilder.buildDescripition(description);
                                }
                                else if (dynamicValue.getPath().endsWith("extension")) { // indicator
                                    String extension = (String) context.resolveExpressionRef(dynamicValue.getExpression()).evaluate(context);
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

    @Search(allowUnknownParams=true)
    public IBundleProvider search(
            javax.servlet.http.HttpServletRequest theServletRequest,
            RequestDetails theRequestDetails,
            @Description(shortDefinition="Search the contents of the resource's data using a fulltext search")
            @OptionalParam(name=ca.uhn.fhir.rest.api.Constants.PARAM_CONTENT)
                    StringAndListParam theFtContent,
            @Description(shortDefinition="Search the contents of the resource's narrative using a fulltext search")
            @OptionalParam(name=ca.uhn.fhir.rest.api.Constants.PARAM_TEXT)
                    StringAndListParam theFtText,
            @Description(shortDefinition="Search for resources which have the given tag")
            @OptionalParam(name=ca.uhn.fhir.rest.api.Constants.PARAM_TAG)
                    TokenAndListParam theSearchForTag,
            @Description(shortDefinition="Search for resources which have the given security labels")
            @OptionalParam(name=ca.uhn.fhir.rest.api.Constants.PARAM_SECURITY)
                    TokenAndListParam theSearchForSecurity,
            @Description(shortDefinition="Search for resources which have the given profile")
            @OptionalParam(name=ca.uhn.fhir.rest.api.Constants.PARAM_PROFILE)
                    UriAndListParam theSearchForProfile,
            @Description(shortDefinition="Return resources linked to by the given target")
            @OptionalParam(name="_has")
                    HasAndListParam theHas,
            @Description(shortDefinition="The ID of the resource")
            @OptionalParam(name="_id")
                    TokenAndListParam the_id,
            @Description(shortDefinition="The language of the resource")
            @OptionalParam(name="_language")
                    StringAndListParam the_language,
            @Description(shortDefinition="What resource is being referenced")
            @OptionalParam(name="composed-of", targetTypes={  } )
                    ReferenceAndListParam theComposed_of,
            @Description(shortDefinition="The plan definition publication date")
            @OptionalParam(name="date")
                    DateRangeParam theDate,
            @Description(shortDefinition="What resource is being referenced")
            @OptionalParam(name="depends-on", targetTypes={  } )
                    ReferenceAndListParam theDepends_on,
            @Description(shortDefinition="What resource is being referenced")
            @OptionalParam(name="derived-from", targetTypes={  } )
                    ReferenceAndListParam theDerived_from,
            @Description(shortDefinition="The description of the plan definition")
            @OptionalParam(name="description")
                    StringAndListParam theDescription,
            @Description(shortDefinition="The time during which the plan definition is intended to be in use")
            @OptionalParam(name="effective")
                    DateRangeParam theEffective,
            @Description(shortDefinition="External identifier for the plan definition")
            @OptionalParam(name="identifier")
                    TokenAndListParam theIdentifier,
            @Description(shortDefinition="Intended jurisdiction for the plan definition")
            @OptionalParam(name="jurisdiction")
                    TokenAndListParam theJurisdiction,
            @Description(shortDefinition="Computationally friendly name of the plan definition")
            @OptionalParam(name="name")
                    StringAndListParam theName,
            @Description(shortDefinition="What resource is being referenced")
            @OptionalParam(name="predecessor", targetTypes={  } )
                    ReferenceAndListParam thePredecessor,
            @Description(shortDefinition="Name of the publisher of the plan definition")
            @OptionalParam(name="publisher")
                    StringAndListParam thePublisher,
            @Description(shortDefinition="The current status of the plan definition")
            @OptionalParam(name="status")
                    TokenAndListParam theStatus,
            @Description(shortDefinition="What resource is being referenced")
            @OptionalParam(name="successor", targetTypes={  } )
                    ReferenceAndListParam theSuccessor,
            @Description(shortDefinition="The human-friendly name of the plan definition")
            @OptionalParam(name="title")
                    StringAndListParam theTitle,
            @Description(shortDefinition="Topics associated with the module")
            @OptionalParam(name="topic")
                    TokenAndListParam theTopic,
            @Description(shortDefinition="The uri that identifies the plan definition")
            @OptionalParam(name="url")
                    UriAndListParam theUrl,
            @Description(shortDefinition="The business version of the plan definition")
            @OptionalParam(name="version")
                    TokenAndListParam theVersion,
            @RawParam
                    Map<String, List<String>> theAdditionalRawParams,
            @IncludeParam(reverse=true)
                    Set<Include> theRevIncludes,
            @Description(shortDefinition="Only return resources which were last updated as specified by the given range")
            @OptionalParam(name="_lastUpdated")
                    DateRangeParam theLastUpdated,
            @IncludeParam(allow= {
                    "PlanDefinition:composed-of" , 					"PlanDefinition:depends-on" , 					"PlanDefinition:derived-from" , 					"PlanDefinition:predecessor" , 					"PlanDefinition:successor" , 						"PlanDefinition:composed-of" , 					"PlanDefinition:depends-on" , 					"PlanDefinition:derived-from" , 					"PlanDefinition:predecessor" , 					"PlanDefinition:successor" , 						"PlanDefinition:composed-of" , 					"PlanDefinition:depends-on" , 					"PlanDefinition:derived-from" , 					"PlanDefinition:predecessor" , 					"PlanDefinition:successor" , 						"PlanDefinition:composed-of" , 					"PlanDefinition:depends-on" , 					"PlanDefinition:derived-from" , 					"PlanDefinition:predecessor" , 					"PlanDefinition:successor" , 						"PlanDefinition:composed-of" , 					"PlanDefinition:depends-on" , 					"PlanDefinition:derived-from" , 					"PlanDefinition:predecessor" , 					"PlanDefinition:successor" 					, "*"
            })
                    Set<Include> theIncludes,
            @Sort
                    SortSpec theSort,
            @ca.uhn.fhir.rest.annotation.Count
                    Integer theCount
    ) {
        startRequest(theServletRequest);
        try {
            SearchParameterMap paramMap = new SearchParameterMap();
            paramMap.add(ca.uhn.fhir.rest.api.Constants.PARAM_CONTENT, theFtContent);
            paramMap.add(ca.uhn.fhir.rest.api.Constants.PARAM_TEXT, theFtText);
            paramMap.add(ca.uhn.fhir.rest.api.Constants.PARAM_TAG, theSearchForTag);
            paramMap.add(ca.uhn.fhir.rest.api.Constants.PARAM_SECURITY, theSearchForSecurity);
            paramMap.add(ca.uhn.fhir.rest.api.Constants.PARAM_PROFILE, theSearchForProfile);
            paramMap.add("_has", theHas);
            paramMap.add("_id", the_id);
            paramMap.add("_language", the_language);
            paramMap.add("composed-of", theComposed_of);
            paramMap.add("date", theDate);
            paramMap.add("depends-on", theDepends_on);
            paramMap.add("derived-from", theDerived_from);
            paramMap.add("description", theDescription);
            paramMap.add("effective", theEffective);
            paramMap.add("identifier", theIdentifier);
            paramMap.add("jurisdiction", theJurisdiction);
            paramMap.add("name", theName);
            paramMap.add("predecessor", thePredecessor);
            paramMap.add("publisher", thePublisher);
            paramMap.add("status", theStatus);
            paramMap.add("successor", theSuccessor);
            paramMap.add("title", theTitle);
            paramMap.add("topic", theTopic);
            paramMap.add("url", theUrl);
            paramMap.add("version", theVersion);
            paramMap.setRevIncludes(theRevIncludes);
            paramMap.setLastUpdated(theLastUpdated);
            paramMap.setIncludes(theIncludes);
            paramMap.setSort(theSort);
            paramMap.setCount(theCount);
//            paramMap.setRequestDetails(theRequestDetails);

            getDao().translateRawParameters(theAdditionalRawParams, paramMap);

            return getDao().search(paramMap, theRequestDetails);
        } finally {
            endRequest(theServletRequest);
        }
    }
}
