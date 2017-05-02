package org.gdharley.activiti.integration.rest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.delegate.Expression;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Created by gharley on 5/2/17.
 */
public class SimpleRestDelegate implements JavaDelegate {
    private static final Logger logger = LoggerFactory.getLogger(SimpleRestDelegate.class);

    protected Expression endpointUrl;
    protected Expression httpMethod;
    protected Expression isSecure;
    protected Expression payload;
    protected Expression headers;
    protected Expression responseMapping;

    protected ObjectMapper objectMapper = new ObjectMapper();
    // Create a mixin to force the BasicNameValuePair constructor
    protected static abstract class BasicNameValuePairMixIn {
        private BasicNameValuePairMixIn(@JsonProperty("name") String name, @JsonProperty("value") String value) { }
    }

    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Started Generic REST call delegate");

        if (endpointUrl == null || httpMethod == null) {
            throw new IllegalArgumentException("An endpoint URL and http method are required");
        }

        String restUrl = getExpressionAsString(endpointUrl, execution);
        String payloadStr = getExpressionAsString(payload, execution);
        String headersJSON = getExpressionAsString(headers, execution); // [{"name":"headerName", "value":"headerValue"}]
        String method = getExpressionAsString(httpMethod, execution);
        String rMapping = getExpressionAsString(responseMapping, execution);
        String secure = getExpressionAsString(isSecure, execution);
        String scheme = secure == "true" ?"https":"http";

        // validate URI and create create request
        URI restEndpointURI = composeURI(restUrl, execution);

        logger.info("Using Generic REST URI " + restEndpointURI.toString());

        HttpRequestBase httpRequest = createHttpRequest(restEndpointURI, scheme, method, headersJSON, payloadStr, rMapping);

        // create http client
        CloseableHttpClient httpClient = createHttpClient(httpRequest, scheme, execution);

        // execute request
        HttpResponse response = executeHttpRequest(httpClient, httpRequest);

        // map response to process instance variables
        if(responseMapping != null){
            mapResponse(response, rMapping, execution);
        }

        logger.info("Ended Generic REST call delegate");

    }

    protected URI composeURI(String restUrl, DelegateExecution execution)
            throws URISyntaxException {

        URIBuilder uriBuilder = null;
        uriBuilder = encodePath(restUrl, uriBuilder);
        return uriBuilder.build();
    }

    protected URIBuilder encodePath(String restUrl, URIBuilder uriBuilder) throws URISyntaxException {

        if (StringUtils.isNotEmpty(restUrl)) {

            // check if there are URL params
            if (restUrl.indexOf('?') > -1) {

                List<NameValuePair> params = URLEncodedUtils.parse(new URI(restUrl), "UTF-8");

                if (params != null && !params.isEmpty()) {
                    restUrl = restUrl.substring(0, restUrl.indexOf('?'));
                    uriBuilder = new URIBuilder(restUrl);
                    uriBuilder.addParameters(params);

                }
            } else {
                uriBuilder = new URIBuilder(restUrl);
            }
        }

        return uriBuilder;
    }

    protected HttpRequestBase createHttpRequest(URI restEndpointURI, String scheme, String httpMethod, String headers, String payload, String responseMapping) {

        if (StringUtils.isEmpty(httpMethod)) {
            throw new ActivitiException("no HTTP method provided");
        }
        if (restEndpointURI == null) {
            throw new ActivitiException("no REST endpoint URI provided");
        }

        HttpRequestBase httpRequest = null;
        HttpMethod parsedMethod = HttpMethod.valueOf(httpMethod.toUpperCase());
        StringEntity input;
        URIBuilder builder = new URIBuilder(restEndpointURI);

        switch (parsedMethod) {
            case GET:
                try {
                    httpRequest = new HttpGet(builder.build());
                    httpRequest = addHeadersToRequest(httpRequest, headers);
                } catch (URISyntaxException use) {
                    throw new ActivitiException("Error while building GET request", use);
                }
                break;
            case POST:
                try {
                    httpRequest = new HttpPost(builder.build());
                    input = new StringEntity(payload);
//				input.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
                    ((HttpPost) httpRequest).setEntity(input);
                    httpRequest = addHeadersToRequest(httpRequest, headers);
                    break;
                } catch (Exception e) {
                    throw new ActivitiException("Error while building POST request", e);
                }
            case PUT:
                try {
                    httpRequest = new HttpPut(builder.build());
                    input = new StringEntity(payload);
//				input.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
                    ((HttpPut) httpRequest).setEntity(input);
                    httpRequest = addHeadersToRequest(httpRequest, headers);
                    break;
                } catch (Exception e) {
                    throw new ActivitiException("Error while building PUT request", e);
                }
            case DELETE:
                try {
                    httpRequest = new HttpDelete(builder.build());
                    httpRequest = addHeadersToRequest(httpRequest, headers);
                } catch (URISyntaxException use) {
                    throw new ActivitiException("Error while building DELETE request", use);
                }
                break;
            default:
                throw new ActivitiException("unknown HTTP method provided");
        }

        return httpRequest;
    }


    protected CloseableHttpClient createHttpClient(HttpRequestBase request, String scheme, DelegateExecution execution) {

        SSLConnectionSocketFactory sslsf = null;

        // Allow self signed certificates and hostname mismatches.
        if (StringUtils.equalsIgnoreCase(scheme, "https")) {
            try {
                SSLContextBuilder builder = new SSLContextBuilder();
                builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                sslsf = new SSLConnectionSocketFactory(builder.build(), SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            } catch (Exception e) {
                logger.warn("Could not configure HTTP client to use SSL", e);
            }
        }

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

        if (sslsf != null) {
            httpClientBuilder.setSSLSocketFactory(sslsf);
        }

        return httpClientBuilder.build();
    }

    protected HttpResponse executeHttpRequest(CloseableHttpClient httpClient, HttpRequestBase httpRequest) {

        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(httpRequest);
        } catch (IOException e) {
            throw new ActivitiException("error while executing http request: " + httpRequest.getURI(), e);
        }

        if (response.getStatusLine().getStatusCode() >= 400) {
            throw new ActivitiException("error while executing http request " + httpRequest.getURI() + " with status code: "
                    + response.getStatusLine().getStatusCode());
        }

        return response;
    }

    protected void mapResponse(HttpResponse response, String responseMapping, DelegateExecution execution) {

        if (responseMapping == null || responseMapping.isEmpty()) {
            return;
        }

        JsonNode jsonNode = null;
        try {
            String jsonString = EntityUtils.toString(response.getEntity());
            jsonNode = objectMapper.readTree(jsonString);

        } catch (Exception e) {
            throw new ActivitiException("error while parsing response", e);
        }

        if (jsonNode == null) {
            throw new ActivitiException("didn't expect an empty response body");
        }
        execution.setVariable(responseMapping, jsonNode.toString());
    }

    protected HttpRequestBase addHeadersToRequest(HttpRequestBase httpRequest, String headerJSON){
        Boolean contentTypeDetected = false;
        if(headerJSON != null){
            // Convert JSON to array
            try {
                // configuration for Jackson/fasterxml
                objectMapper.addMixInAnnotations(BasicNameValuePair.class, BasicNameValuePairMixIn.class);
                NameValuePair[] headers = objectMapper.readValue(headerJSON, BasicNameValuePair[].class);
                for(NameValuePair header : headers)
                {
                    httpRequest.addHeader(header.getName(), header.getValue());
                    if(header.getName().equals(HTTP.CONTENT_TYPE)){
                        contentTypeDetected = true;
                    }
                }
            } catch (Exception e) {
                throw new ActivitiException("Unable to parse JSON header array", e);
            }
        }
        // Now add content type if necessary
        if(!contentTypeDetected){
            httpRequest.addHeader(HTTP.CONTENT_TYPE, "application/json");
        }
        return httpRequest;
    }

    /**
     * @return string value of expression.
     * @throws {@link IllegalArgumentException} when the expression resolves to a value which is not a string
     * or if the value is null.
     */
    protected String getExpressionAsString(Expression expression, DelegateExecution execution) {
        if(expression == null){
            return null;
        } else {
            Object value = expression.getValue(execution);
            if(value instanceof String) {
                return (String) value;
            } else {
                throw new IllegalArgumentException("Expression does not resolve to a string or is null: " + expression.getExpressionText());
            }
        }
    }
}
