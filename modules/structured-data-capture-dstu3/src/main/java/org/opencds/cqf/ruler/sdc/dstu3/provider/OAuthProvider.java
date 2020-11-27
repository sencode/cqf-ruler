package org.opencds.cqf.ruler.sdc.dstu3.provider;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.dstu3.model.CapabilityStatement;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.UriType;
import org.opencds.cqf.ruler.common.dstu3.provider.CqfRulerJpaConformanceProviderDstu3;
import org.opencds.cqf.ruler.sdc.config.OAuthConfig;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.api.server.RequestDetails;

/**
 *      This class is NOT designed to be a real OAuth provider.
 *      It is designed to provide a capability statement and to pass thru the path to the real oauth verification server.
 *      It should only get instantiated if hapi.properties has oauth.enabled set to true.
 */
@Component
public class OAuthProvider extends CqfRulerJpaConformanceProviderDstu3 {

    private OAuthConfig oAuthConfig;

    public OAuthProvider(OAuthConfig oAuthConfig) {
        this.oAuthConfig = oAuthConfig;
    }
    
    @Metadata
    @Override
    public CapabilityStatement getServerConformance(HttpServletRequest theRequest, RequestDetails theRequestDetails) {
        CapabilityStatement retVal;
        retVal = super.getServerConformance(theRequest, theRequestDetails);

        retVal.getRestFirstRep().getSecurity().setCors(oAuthConfig.getOauthSecurityCors());
        Extension securityExtension = retVal.getRestFirstRep().getSecurity().addExtension();
        securityExtension.setUrl(oAuthConfig.getOauthSecurityUrl());
        // security.extension.extension
        Extension securityExtExt = securityExtension.addExtension();
        securityExtExt.setUrl(oAuthConfig.getOauthSecurityExtAuthUrl());
        securityExtExt.setValue(new UriType(oAuthConfig.getOauthSecurityExtAuthValueUri()));
        Extension securityTokenExt = securityExtension.addExtension();
        securityTokenExt.setUrl(oAuthConfig.getOauthSecurityExtTokenUrl());
        securityTokenExt.setValue(new UriType(oAuthConfig.getOauthSecurityExtTokenValueUri()));

        // security.extension.service
        Coding coding = new Coding();
        coding.setSystem(oAuthConfig.getOauthServiceSystem());
        coding.setCode(oAuthConfig.getOauthServiceCode());
        coding.setDisplay(oAuthConfig.getOauthServiceDisplay());
        CodeableConcept codeConcept = new CodeableConcept();
        codeConcept.addCoding(coding);
        retVal.getRestFirstRep().getSecurity().getService().add(codeConcept);
        // retVal.getRestFirstRep().getSecurity().getService() //how do we handle "text" on the sample not part of getService

        return retVal;
    }

}
