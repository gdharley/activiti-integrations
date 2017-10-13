package org.gdharley.activiti.integration.soap;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;


/**
 * Created by gharley on 10/13/17.
 */
public class SimpleSOAPDelegate implements JavaDelegate {
    private static final Logger logger = LoggerFactory.getLogger(SimpleSOAPDelegate.class);


    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Started Generic SOAP call delegate");

        String var = (String) execution.getVariable("ip");

        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();

        String serverURI = "http://www.webservicex.net/";

        // SOAP Envelope
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("example", serverURI);

        // SOAP Body
        SOAPBody soapBody = envelope.getBody();
        SOAPElement soapBodyElem = soapBody.addChildElement("GetGeoIP", "example");
        SOAPElement soapBodyElem1 = soapBodyElem.addChildElement("IPAddress", "example");
        soapBodyElem1.addTextNode(var);

        MimeHeaders headers = soapMessage.getMimeHeaders();
        headers.addHeader("SOAPAction", serverURI + "GetGeoIP");

        soapMessage.saveChanges();

        // Create SOAP Connection
        SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection soapConnection = soapConnectionFactory.createConnection();

        // Send SOAP Message to SOAP Server
        String url = "http://www.webservicex.net/geoipservice.asmx";
        SOAPMessage soapResponse = soapConnection.call(soapMessage, url);

        String result = "";
        NodeList nodeList = soapResponse.getSOAPBody().getElementsByTagName("CountryName");
        if (nodeList.getLength() > 0)
            result = nodeList.item(0).getTextContent();
        else
            result = "Invalid IP address";

        soapConnection.close();

        execution.setVariable("result", result);

        logger.info("Ended Generic SOAP call delegate");

    }
}
