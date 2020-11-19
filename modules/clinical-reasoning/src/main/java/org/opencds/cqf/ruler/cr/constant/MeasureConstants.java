package org.opencds.cqf.ruler.cr.constant;

import java.util.HashMap;
import java.util.Map;

public class MeasureConstants {

    // private static Map<String, String> measureTypeValueSetMap = new HashMap<String, String>() {
    //     private static final long serialVersionUID = 1L;
    //     {
    //         put("PROCESS", "Process");
    //         put("OUTCOME", "Outcome");
    //         put("STRUCTURE", "Structure");
    //         put("PATIENT-REPORTED-OUTCOME", "Patient Reported Outcome");
    //         put("COMPOSITE", "Composite");
    //     }
    // };

    // private static Map<String, String> measureScoringValueSetMap = new HashMap<String, String>() {
    //     private static final long serialVersionUID = 1L;
    //     {
    //         put("PROPOR", "Proportion");
    //         put("RATIO", "Ratio");
    //         put("CONTINUOUS-VARIABLE", "Continuous Variable");
    //         put("COHORT", "Cohort");
    //     }
    // };

    public static class CodeMapping {
        public CodeMapping(String code, String displayName, String criteriaName, String criteriaExtension) {
            this.code = code;
            this.displayName = displayName;
            this.criteriaName = criteriaName;
            this.criteriaExtension = criteriaExtension;
        }

        public String code;
        public String displayName;
        public String criteriaName;
        public String criteriaExtension;

    }

    public static Map<String, CodeMapping> measurePopulationValueSetMap = new HashMap<String, CodeMapping>() {
        private static final long serialVersionUID = 1L;

        {
            put("initial-population", new CodeMapping("IPOP", "Initial Population", "initialPopulationCriteria", "initialPopulation"));
            put("numerator", new CodeMapping("NUMER", "Numerator", "numeratorCriteria", "numerator"));
            put("numerator-exclusion", new CodeMapping("NUMEX", "Numerator Exclusion", "numeratorExclusionCriteria", "numeratorExclusions"));
            put("denominator", new CodeMapping("DENOM", "Denominator", "denominatorCriteria", "denominator"));
            put("denominator-exclusion", new CodeMapping("DENEX", "Denominator Exclusion", "denominatorExclusionCriteria", "denominatorExclusions"));
            put("denominator-exception", new CodeMapping("DENEXCEP", "Denominator Exception", "denominatorExceptionCriteria", "denominatorExceptions"));
            // TODO: Figure out what the codes for these are (MPOP, MPOPEX, MPOPEXCEP are guesses)
            put("measure-population", new CodeMapping("MPOP", "Measure Population", "measurePopulationCriteria", "measurePopulation"));
            put("measure-population-exclusion", new CodeMapping("MPOPEX", "Measure Population Exclusion", "measurePopulationExclusionCriteria", "measurePopulationExclusions"));
            put("measure-observation", new CodeMapping("MOBS", "Measure Observation", "measureObservationCriteria", "measureObservations"));
        }
    };
    
}
