package org.opencds.cqf.ruler.common.r4.builder;

import java.util.Date;

import org.hl7.fhir.r4.model.Period;
import org.opencds.cqf.ruler.common.builder.BaseBuilder;

public class PeriodBuilder extends BaseBuilder<Period> {

    public PeriodBuilder() {
        super(new Period());
    }

    public PeriodBuilder buildStart(Date start) {
        complexProperty.setStart(start);
        return this;
    }

    public PeriodBuilder buildEnd(Date end) {
        complexProperty.setEnd(end);
        return this;
    }
}
