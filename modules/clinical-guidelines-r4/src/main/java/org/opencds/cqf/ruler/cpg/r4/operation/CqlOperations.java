package org.opencds.cqf.ruler.cpg.r4.operation;

import javax.inject.Inject;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.opencds.cqf.ruler.common.r4.provider.CqlExecutionProvider;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;

/**
 * Created by Bryn on 1/16/2017.
 */
@Component
public class CqlOperations {
    private CqlExecutionProvider cqlExecutionProvider;

    @Inject
    public CqlOperations(CqlExecutionProvider cqlExecutionProvider) {
        this.cqlExecutionProvider = cqlExecutionProvider;
    }

    @Operation(name = "$cql")
    public Bundle evaluate(@OperationParam(name = "code") String code,
            @OperationParam(name = "patientId") String patientId,
            @OperationParam(name = "periodStart") String periodStart,
            @OperationParam(name = "periodEnd") String periodEnd,
            @OperationParam(name = "productLine") String productLine,
            @OperationParam(name = "terminologyServiceUri") String terminologyServiceUri,
            @OperationParam(name = "terminologyUser") String terminologyUser,
            @OperationParam(name = "terminologyPass") String terminologyPass,
            @OperationParam(name = "context") String contextParam,
            @OperationParam(name = "parameters") Parameters parameters) {

        return this.cqlExecutionProvider.evaluate(code, patientId, periodStart, periodEnd, productLine, terminologyServiceUri, terminologyUser, terminologyPass, contextParam, null, parameters);
    }


}
