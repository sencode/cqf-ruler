package org.opencds.cqf.r4.execution;

import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Endpoint;

public interface ICarePlanProcessor<C> {
    public IAnyResource execute(C carePlan);
    public IAnyResource execute(C carePlan, Endpoint endpoint);
}