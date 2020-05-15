package org.opencds.cqf.r4.execution;

import org.hl7.fhir.instance.model.api.IAnyResource;

public interface ITaskProcessor<T> {
    public IAnyResource execute(T task);
    public void update(T task);
}