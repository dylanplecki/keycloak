package org.keycloak.saml;

import org.keycloak.saml.common.constants.JBossSAMLURIConstants;
import org.keycloak.saml.common.exceptions.ProcessingException;
import org.keycloak.saml.processing.api.saml.v2.response.SAML2Response;
import org.keycloak.saml.processing.core.saml.v2.common.IDGenerator;
import org.keycloak.saml.processing.core.saml.v2.factories.JBossSAMLAuthnResponseFactory;
import org.keycloak.saml.processing.core.saml.v2.holders.IDPInfoHolder;
import org.keycloak.saml.processing.core.saml.v2.holders.IssuerInfoHolder;
import org.keycloak.saml.processing.core.saml.v2.holders.SPInfoHolder;
import org.keycloak.dom.saml.v2.protocol.ResponseType;
import org.w3c.dom.Document;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class SAML2ErrorResponseBuilder {

    protected String status;
    protected String destination;
    protected String issuer;

    public SAML2ErrorResponseBuilder status(String status) {
        this.status = status;
        return this;
    }

    public SAML2ErrorResponseBuilder destination(String destination) {
        this.destination = destination;
        return this;
    }

    public SAML2ErrorResponseBuilder issuer(String issuer) {
        this.issuer = issuer;
        return this;
    }


    public Document buildDocument() throws ProcessingException {
        Document samlResponse = null;
        ResponseType responseType = null;

        SAML2Response saml2Response = new SAML2Response();

        // Create a response type
        String id = IDGenerator.create("ID_");

        IssuerInfoHolder issuerHolder = new IssuerInfoHolder(issuer);
        issuerHolder.setStatusCode(status);

        IDPInfoHolder idp = new IDPInfoHolder();
        idp.setNameIDFormatValue(null);
        idp.setNameIDFormat(JBossSAMLURIConstants.NAMEID_FORMAT_PERSISTENT.get());

        SPInfoHolder sp = new SPInfoHolder();
        sp.setResponseDestinationURI(destination);

        responseType = saml2Response.createResponseType(id);
        responseType.setStatus(JBossSAMLAuthnResponseFactory.createStatusTypeForResponder(status));
        responseType.setDestination(destination);

        return samlResponse;
    }


}
