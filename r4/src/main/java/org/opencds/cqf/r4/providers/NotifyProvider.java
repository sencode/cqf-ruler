package org.opencds.cqf.r4.providers;

import java.io.IOException;

import javax.xml.bind.JAXBException;

import org.opencds.cqf.cql.service.factory.DataProviderFactory;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Resource;
import org.opencds.cqf.common.providers.LibraryResolutionProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.opencds.cqf.r4.managers.ReportingManager;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;

public class NotifyProvider {

    private DaoRegistry registry;
    private LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResourceProvider;
    private DataProviderFactory dataProviderFactory;
    private FhirContext fhirContext;
    private TerminologyProvider localSystemTerminologyProvider;

    public NotifyProvider(DaoRegistry registry, LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResourceProvider, DataProviderFactory dataProviderFactory, FhirContext fhirContext, TerminologyProvider localSystemTerminologyProvider) {
        this.registry = registry;
        this.libraryResourceProvider = libraryResourceProvider;
        this.dataProviderFactory = dataProviderFactory;
        this.fhirContext = fhirContext;
        this.localSystemTerminologyProvider = localSystemTerminologyProvider;
    }

    /*
        TODO: add Subscribe functionality
    */
    @Operation(name = "$notify", idempotent = true)
    public IAnyResource notify(
            @OperationParam(name ="patientId") String patientId,
            @OperationParam(name="encounterId") String encounterId,
            @OperationParam(name="workFlowEndpoint") Endpoint workFlowEndpoint) throws FHIRException, IOException, JAXBException {
                // How do I know which PlanDefinition to apply
                String eRSDId = "plandefinition-RuleFilters-1.0.0";
        ReportingManager reportingManager = new ReportingManager(fhirContext, registry, libraryResourceProvider, dataProviderFactory, localSystemTerminologyProvider);
        return reportingManager.manage(eRSDId, patientId, workFlowEndpoint);
    }
}