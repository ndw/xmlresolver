package org.xmlresolver;

import org.xmlresolver.utils.RsrcUtils;

import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ResolvedResourceImpl extends ResolvedResource {
    private final URI resolvedURI;
    private final URI localURI;
    private final InputStream inputStream;
    private final String contentType;
    private final Map<String, List<String>> headers;
    private final int statusCode;

    public ResolvedResourceImpl(URI resolvedURI, URI localURI, InputStream stream, int status, Map<String,List<String>> headers) {
        this.resolvedURI = resolvedURI;
        this.localURI = localURI;
        this.inputStream = stream;
        this.headers = headers;

        String ctype = null;
        for (String name : headers.keySet()) {
            if ("content-type".equalsIgnoreCase(name)) {
                ctype = headers.get(name).get(0);
            }
        }
        this.contentType = ctype;
        this.statusCode = status;
    }

    @Override
    public ResourceRequest getRequest() {
        throw new RuntimeException("getRequest not implemented");
    }

    @Override
    public URI getResolvedURI() {
        return resolvedURI;
    }

    @Override
    public URI getLocalURI() {
        return localURI;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public Map<String,List<String>> getHeaders() {
        return headers;
    }

    public String getHeader(String headerName) {
        return RsrcUtils.getHeader(headerName, headers);
    }

    public String getEncoding() {
        throw new RuntimeException("getRequest not implemented");
    }

}
