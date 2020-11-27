package org.opencds.cqf.ruler.common.r4.builder;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.opencds.cqf.ruler.common.builder.BaseBuilder;

public class CodeableConceptBuilder extends BaseBuilder<CodeableConcept> {

    public CodeableConceptBuilder() {
        super(new CodeableConcept());
    }

    public CodeableConceptBuilder buildCoding(List<Coding> coding) {
        complexProperty.setCoding(coding);
        return this;
    }

    public CodeableConceptBuilder buildCoding(Coding coding) {
        if (!complexProperty.hasCoding()) {
            complexProperty.setCoding(new ArrayList<>());
        }

        complexProperty.addCoding(coding);
        return this;
    }

    public CodeableConceptBuilder buildText(String text) {
        complexProperty.setText(text);
        return this;
    }
}
