package org.xmlresolver;

import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates the response to a resource request.
 */
public class ResourceResponse extends ResolvedResource {
    private final ResourceRequest request;
    private final URI uri;
    private final URI localUri;
    private final Map<String,List<String>> headers = new HashMap<>();
    private InputStream stream = null;
    private String contentType = null;
    private String encoding = null;
    private int statusCode = -1;

    protected ResourceResponse(ResourceRequest request) {
        this(request, null, null);
    }

    protected ResourceResponse(ResourceRequest request, URI uri) {
        this(request, uri, null);
    }

    protected ResourceResponse(ResourceRequest request, URI uri, URI localUri) {
        this.request = request;
        this.uri = uri;
        this.localUri = localUri;
    }

    /**
     * What request generated this response?
     * <p>As a convenience, you can get back the request for which this response was constructed.</p>
     * @return the originating request
     */
    public ResourceRequest getRequest() {
        return request;
    }

    /**
     * Did the resolution succeed?
     * @return true if the resource was successfully resolved
     */
    public boolean isResolved() {
        return uri != null;
    }

    /**
     * Get the resolved URI
     * @return the resolved URI, or {@code null} if the resolution failed. See also {@link #getLocalURI()}.
     */
    @Override
    public URI getResolvedURI() {
        return uri;
    }

    /**
     * Get the local version of the resolved URI.
     * <p>The resolved URI and the local URI are usually the same. But if the resolved URI is
     * masked (for example, if it's a {@code jar:} or {@code classpath:} URI and the
     * {@link ResolverFeature#MASK_JAR_URIS} feature is enabled, the resolved URI will be
     * the original request URI (made absolute). The local URI will be the underlying
     * {@code jar:} or {@code classpath:} URI.</p>
     * @return the local URI
     */
    @Override
    public URI getLocalURI() {
        return localUri;
    }

    protected void setHeaders(Map<String, List<String>> headers) {
        this.headers.clear();
        this.headers.putAll(headers);
    }

    /**
     * Returns headers associated with the response
     * @return the headers or an empty map if no headers are available.
     */
    @Override
    public Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Return the (first) header with the specified name.
     * <p>This is a convenience method partly because header names are case-insensitive. Returns
     * only the first value, or null if there are no values.</p>
     *
     * @param name the name of the header whose value should be returned.
     * @return the value of the first header with the specified name
     * @throws NullPointerException if name is null
     */
    @Override
    public String getHeader(String name) {
        if (name == null) {
            throw new NullPointerException("Header name is null");
        }
        Map<String, List<String>> headers = getHeaders();
        for (String header : headers.keySet()) {
            if (name.equalsIgnoreCase(header)) {
                List<String> values = headers.get(header);
                return values == null || values.isEmpty() ? null : values.get(0);
            }
        }
        return null;
    }

    protected void setInputStream(InputStream stream) {
        this.stream = stream;
    }

    /**
     * Get the input stream to read the resource
     * @return the stream, or {@code null} if no stream is available
     */
    @Override
    public InputStream getInputStream() {
        return stream;
    }

    protected void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Get the content type of the resource
     * @return the content type or null if the content type is not known
     */
    @Override
    public String getContentType() {
        return contentType != null ? contentType : getHeader("content-type");
    }

    protected void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Get the encoding of the resource
     * <p>Extracts the encoding from the content type header, if possible. Returns {@code null} if
     * the encoding is unknown. Note that an XML resource may have an encoding declaration, but that
     * is not used by this method.</p>
     * @return the encoding, or null if the encoding is unknown
     */
    public String getEncoding() {
        if (encoding != null) {
            return encoding;
        }

        String ctype = getHeader("content-type");
        if (ctype == null) {
            return null;
        }

        Pattern charset = Pattern.compile("^.*;\\s*charset=([^;]+).*$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = charset.matcher(ctype);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        return null;
    }

    protected void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Get the response status code
     * <p>For successful resolution to {@code file:} resources, the code is set to 200 for consistency
     * with {@code http:} (and {@code https:}).</p>
     * @return the status code, or -1 if it's unknown or unavailable.
     */
    @Override
    public int getStatusCode() {
        return statusCode;
    }
}
