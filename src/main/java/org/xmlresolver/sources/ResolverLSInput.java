package org.xmlresolver.sources;

import org.w3c.dom.ls.LSInput;
import org.xmlresolver.ResourceResponse;
import org.xmlresolver.utils.RsrcUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** An {@link LSInput} with a <code>resolvedURI</code>.
 *
 */
public class ResolverLSInput implements LSInput, ResolverResourceInfo {
    private final ResourceResponse response;
    private final URI resolvedURI;
    final String publicId;
    final String systemId;
    final InputStream body;
    final URI uri;
    private final int statusCode;
    private final Map<String, List<String>> resolvedHeaders;

    /** Construct the {@link org.w3c.dom.ls.LSInput} while preserving the local URI.
     *
     * If the resolved resource is available, we can get everything except the
     * public identifier from that resolved resource.
     *
     * @param resp The resource response.
     * @param publicId The publicId.
     * */
    public ResolverLSInput(ResourceResponse resp, String publicId) {
        resolvedURI = resp.getResolvedURI();
        this.response = resp;
        this.body = resp.getInputStream();
        this.systemId = resp.getResolvedURI().toString();
        this.publicId = publicId;
        this.uri = resp.getURI();
        this.statusCode = resp.getStatusCode();
        this.resolvedHeaders = resp.getHeaders();
    }

    /** The LSInput API... */
    public Reader getCharacterStream() {
        return new InputStreamReader(body);
    }

    /** The LSInput API...
     *
     * This method always throws an {@link UnsupportedOperationException}.
     * */
    public void setCharacterStream(Reader reader) {
        throw new UnsupportedOperationException("Can't set character stream on resolver LSInput");
    }

    /** The LSInput API... */
    public InputStream getByteStream() {
        return body;
    }

    /** The LSInput API...
     *
     * This method always throws an {@link UnsupportedOperationException}.
     * */
    public void setByteStream(InputStream inputStream) {
        throw new UnsupportedOperationException("Can't set byte stream on resolver LSInput");
    }

    /** The LSInput API... */
    public String getStringData() {
        // The data is in the stream
        return null;
    }

    /** The LSInput API...
     *
     * This method always throws an {@link UnsupportedOperationException}.
     * */
    public void setStringData(String string) {
        throw new UnsupportedOperationException("Can't set string data on resolver LSInput");
    }

    /** The LSInput API... */
    public String getSystemId() {
        return systemId;
    }

    /** The LSInput API...
     *
     * This method always throws an {@link UnsupportedOperationException}.
     * */
    public void setSystemId(String string) {
        throw new UnsupportedOperationException("Can't set system ID on resolver LSInput");
    }

    /** The LSInput API... */
    public String getPublicId() {
        return publicId;
    }

    /** The LSInput API...
     *
     * This method always throws an {@link UnsupportedOperationException}.
     * */
    public void setPublicId(String string) {
        throw new UnsupportedOperationException("Can't set public ID on resolver LSInput");
    }

    /** The LSInput API... */
    public String getBaseURI() {
        return uri.toString();
    }

    /** The LSInput API...
     *
     * This method always throws an {@link UnsupportedOperationException}.
     * */
    public void setBaseURI(String string) {
        throw new UnsupportedOperationException("Can't set base URI on resolver LSInput");
    }

    /** The LSInput API... */
    public String getEncoding() {
        return null; // Unknown
    }

    /** The LSInput API...
     *
     * This method always throws an {@link UnsupportedOperationException}.
     * */
    public void setEncoding(String string) {
        throw new UnsupportedOperationException("Can't set encoding on resolver LSInput");
    }

    /** The LSInput API... */
    public boolean getCertifiedText() {
        return false;
    }

    /** The LSInput API...
     *
     * This method always throws an {@link UnsupportedOperationException}.
     * */
    public void setCertifiedText(boolean b) {
        throw new UnsupportedOperationException("Can't set certified text on resolver LSInput");
    }

    @Override
    public ResourceResponse getResponse() {
        return response;
    }

    @Override
    public URI getResolvedURI() {
        return resolvedURI;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return resolvedHeaders;
    }

    @Override
    public String getHeader(String headerName) {
        return RsrcUtils.getHeader(headerName, resolvedHeaders);
    }
}
