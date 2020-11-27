package org.opencds.cqf.ruler.common.dstu3.builder;

import java.util.Date;

import org.opencds.cqf.ruler.common.builder.BaseBuilder;
import org.opencds.cqf.cql.engine.runtime.DateTime;

public class JavaDateBuilder extends BaseBuilder<Date> {

    public JavaDateBuilder() {
        super(new Date());
    }

    public JavaDateBuilder buildFromDateTime(DateTime dateTime) {
        complexProperty = Date.from(dateTime.getDateTime().toInstant());
        return this;
    }
}
