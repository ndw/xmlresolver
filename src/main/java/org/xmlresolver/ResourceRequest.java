package org.xmlresolver;

import org.xmlresolver.utils.URIUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Encapsulates a request for a resource.
 * <p>The underlying APIs work against requests of this form. The method-based resolver APIs
 * all convert their arguments into a request and issue it against the resolver. If you are only
 * interested in finding out what is in the catalog, pass the request to a {@link CatalogQuerier}.
 * To get a resolved resource, pass it to {@link ResourceResolver}.</p>
 * <p>A request can be constructed by calling {@code getRequest} methods on {@link Resolver}
 * Additional properties can then be added with setter methods.</p>
 *
 * <p>When resolving resources for an XML parser, the resources are identified with "external
 * identifiers" that consist of a system identifier and a public identifier. In some APIs, the entity name
 * is also considered part of the identifier. The external identifier is something of a legacy from SGML.
 * Most formats that were developed later us only the URI as an identifier. The resolver attempts to
 * cover both of these use cases, blurring the distinctions when it's convenient.</p>
 *
 * <p>If a request identifies it's nature as either {@link ResolverConstants#EXTERNAL_ENTITY_NATURE}
 * or {@link ResolverConstants#DTD_NATURE}, it is treated as an external identifier for the purposes
 * of resolution. Otherwise, it is treated as a URI.</p>
 */
public class ResourceRequest {
    private final XMLResolverConfiguration config;
    private final String nature;
    private final String purpose;
    private String uri = null;
    private String baseURI = null;
    private String entityName = null;
    private String publicId = null;
    private String encoding = null;
    private boolean openStream = true;
    private boolean followRedirects = true;

    protected ResourceRequest(XMLResolverConfiguration config, String nature, String purpose) {
        this.config = config;
        this.nature = nature;
        this.purpose = purpose;
    }

    /**
     * Sets the URI (and system identifier) of the request.
     * <p>The request has only a single URI. For convenience, it can be retrieved with either
     * the {@link #getUri()} or {@link #getSystemId()} methods.</p>
     * @param uri the request URI
     */
    protected void setUri(String uri) {
        if (uri != null) {
            if (URIUtils.isWindows() && config.getFeature(ResolverFeature.FIX_WINDOWS_SYSTEM_IDENTIFIERS)) {
                this.uri = URIUtils.windowsPathURI(uri);
            } else {
                this.uri = uri;
            }
        }
    }

    /**
     * Set the base URI.
     * <p>The base URI should be an absolute URI.</p>
     * @param baseURI the request base URI
     */
    protected void setBaseURI(String baseURI) {
        if (baseURI != null) {
            if (URIUtils.isWindows() && config.getFeature(ResolverFeature.FIX_WINDOWS_SYSTEM_IDENTIFIERS)) {
                this.baseURI = URIUtils.windowsPathURI(baseURI);
            } else {
                this.baseURI = baseURI;
            }
        }
    }

    /**
     * Set the entity name.
     * <p>The entity name is only relevant for some kinds of requests.</p>
     * @param name the entity name
     */
    protected void setEntityName(String name) {
        this.entityName = name;
    }

    /**
     * Get the request URI.
     * See {@link #setUri(String)}.
     * @return the request URI.
     */
    public String getUri() {
        return uri;
    }

    /**
     * Get the base URI.
     * @return the base URI
     */
    public String getBaseURI() {
        return baseURI;
    }

    /**
     * Get the absolute URI.
     * <p>This method combines the URI and base URI, returning an absolute URI.</p>
     * <p>If the request has a base URI, and that URI is absolute, the URI returned will
     * be the request URI made absolute with respect to the base URI.</p>
     * <p>If the request doesn't have a base URI (or if the base URI isn't absolute),
     * the request URI will be returned if it's an absolute URI. Otherwise, {@code null} is returned.</p>
     * @return the absolute URI
     * @throws URISyntaxException if the URI or base URI are syntactically invalid
     */
    public URI getAbsoluteURI() throws URISyntaxException {
        if (baseURI != null) {
            URI abs = new URI(baseURI);
            if (abs.isAbsolute()) {
                if (uri == null || uri.isEmpty()) {
                    return abs;
                }
                return abs.resolve(uri);
            }
        }

        if (uri != null) {
            URI abs = new URI(uri);
            if (abs.isAbsolute()) {
                return abs;
            }
        }

        return null;
    }

    /**
     * Get the nature of the request.
     * @return the nature
     */
    public String getNature() {
        return nature;
    }

    /**
     * Get the purpose of the request.
     * @return the purpose
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * Get the system identifier of the request.
     * <p>This method returns the request URI, it's synonymous with {@link #getUri()}.</p>
     * @return the request URI
     */
    public String getSystemId() {
        return uri;
    }

    /**
     * Set the request public identifier.
     * <p>Public identifiers only apply to requests to resolve system identifiers.</p>
     * @param publicId the public identifier
     */
    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    /**
     * Get the public identifier
     * @return the request public identifier
     */
    public String getPublicId() {
        return publicId;
    }

    /**
     * Get the entity name
     * @return the request entity name
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * Set the requested encoding.
     * <p>Not all protocols or systems provide the ability to make the encoding part of the request,
     * but on those that do, this encoding will be used.</p>
     * @param encoding the request encoding
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Get the requested encoding.
     * @return the request encoding
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Return a readable stream?
     * <p>If open stream is true, a resource response will include a stream that can be used
     * to read the response. If you don't need the stream, you can set this flag to false to
     * prevent it from being returned.</p>
     * <p>Setting this flag to false <em>does not</em> guarantee that no requests will
     * be issued. For example, the resovler may follow redirects to find the final URI, even if
     * it doesn't return a stream.</p>
     * <p>The {@link CatalogQuerier} never opens a stream so this setting is irrelevant in that context.</p>
     * @param open true if the open stream should be returned
     */
    public void setOpenStream(boolean open) {
        openStream = open;
    }

    /**
     * Will a readable stream be returned?
     * @return the open stream setting
     */
    public boolean openStream() {
        return openStream;
    }

    /**
     * Follow redirects?
     * <p>If this flag is true, and the URI supports redirects (for example, as http: and https: do),
     * this flag determines whether redirects will be followed. If redirects are followed, only the last
     * URI, the one that isn't a redirect, will be returned.</p>
     * <p>The {@link CatalogQuerier} never makes a request so this setting is irrelevant in that context.</p>
     * @param follow true if redirects should be followed
     */
    public void setFollowRedirects(boolean follow) {
        followRedirects = follow;
    }

    /**
     * Will redirects be followed?
     * @return true if redirects will be followed
     */
    public boolean followRedirects() {
        return followRedirects;
    }
}
