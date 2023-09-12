package org.xmlresolver;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlresolver.logging.AbstractLogger;
import org.xmlresolver.logging.ResolverLogger;
import org.xmlresolver.sources.ResolverInputSource;
import org.xmlresolver.sources.ResolverLSInput;
import org.xmlresolver.sources.ResolverSAXSource;
import org.xmlresolver.utils.URIUtils;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Stack;

/** An implementation of many resolver interfaces.
 *
 * <p>This class is probably the most common entry point to the XML Catalog resolver. It has a zero
 * argument constructor so it can be instantiated directly from its class name (for example, passed to
 * an application as a commend line argument or stored in a configuration file). When instantiated
 * this way, it will automatically be configured by system properties and an <code>xmlresolver.properties</code>
 * configuration file, if one exists.</p>
 *
 * <p>This class implements the {@link org.xml.sax.EntityResolver}, {@link org.xml.sax.ext.EntityResolver2},
 * {@link LSResourceResolver}
 * and {@link org.xmlresolver.NamespaceResolver}, and {@link javax.xml.transform.URIResolver} interfaces.</p>
 *
 * <p>The StAX {@link javax.xml.stream.XMLResolver} interface is implemented by the
 * {@link org.xmlresolver.StAXResolver} class because the <code>resolveEntity</code> method
 * of the <code>XMLResolver</code> interface isn't compatible with the <code>EntityResolver2</code>
 * method of the same name.</p>
 *
 * @see org.xmlresolver.StAXResolver
 */

public class Resolver implements URIResolver, EntityResolver, EntityResolver2, NamespaceResolver, LSResourceResolver {
    public static final int FOLLOW_REDIRECT_LIMIT = 64;

    protected final ResolverLogger logger;
    protected final XMLResolverConfiguration config;
    protected final CatalogQuerier catalogResolver;
    protected CatalogResolver apiCompatibleResolver = null;

    /**
     * Create a resolver with a default configuration.
     */
    public Resolver() {
        config = new XMLResolverConfiguration();
        catalogResolver = new CatalogQuerier(config);
        logger = config.getFeature(ResolverFeature.RESOLVER_LOGGER);
    }

    /**
     * Create a resolver with a specific configuration
     * @param config the configuration
     */
    public Resolver(XMLResolverConfiguration config) {
        this.config = config;
        catalogResolver = new CatalogQuerier(config);
        logger = config.getFeature(ResolverFeature.RESOLVER_LOGGER);
    }

    /**
     * Create a resolver from a specific catalog resolver
     * <p>This constructor creates a resolver with the same configuration as the catalog resolver.</p>
     * <p>The catalog resolver is a backwards compatible API; it has been rewritten in terms of this
     * resolver and the {@link CatalogQuerier}.</p>
     * @param resolver the resolver
     */
    public Resolver(CatalogResolver resolver) {
        this.config = resolver.getConfiguration();
        catalogResolver = new CatalogQuerier(config);
        apiCompatibleResolver = resolver;
        logger = config.getFeature(ResolverFeature.RESOLVER_LOGGER);
    }

    /**
     * Get the configuration used by this resolver.
     * @return the configuration
     */
    public XMLResolverConfiguration getConfiguration() {
        return config;
    }

    /**
     * Return the underlying catalog resolver.
     * <p>The catalog resolver is a backwards compatible API; it has been rewritten in terms of this
     * resolver and the {@link CatalogQuerier}.</p>
     * @return the resolver
     */
    public CatalogResolver getCatalogResolver() {
        if (apiCompatibleResolver == null) {
            apiCompatibleResolver = new CatalogResolver(config);
        }
        return apiCompatibleResolver;
    }

    /** What version is this?
     * <p>Returns the version number of this resolver instance.</p>
     * @return The version number
     */
    public static String version() {
        return BuildConfig.VERSION;
    }

    /**
     * Create a request with a URI.
     * @param uri the URI
     * @return the request
     */
    public ResourceRequest getRequest(String uri) {
        ResourceRequest req = new ResourceRequest(config, ResolverConstants.ANY_NATURE, ResolverConstants.ANY_PURPOSE);
        req.setUri(uri);
        return req;
    }

    /**
     * Create a request with a URI and a base URI.
     * @param uri the URI
     * @param baseURI the base URI
     * @return the request
     */
    public ResourceRequest getRequest(String uri, String baseURI) {
        ResourceRequest req = new ResourceRequest(config, ResolverConstants.ANY_NATURE, ResolverConstants.ANY_PURPOSE);
        req.setUri(uri);
        req.setBaseURI(baseURI);
        return req;
    }

    /**
     * Create a request for a URI with a specific nature and purpose.
     * @param uri the URI
     * @param nature the nature
     * @param purpose the purpose
     * @return the request
     */
    public ResourceRequest getRequest(String uri, String nature, String purpose) {
        ResourceRequest req = new ResourceRequest(config, nature, purpose);
        req.setUri(uri);
        return req;
    }

    /**
     * Create a request for a URI and a base URI with a specific nature and purpose.
     * @param uri the URI
     * @param baseURI the base URI
     * @param nature the nature
     * @param purpose the purpose
     * @return the request
     */
    public ResourceRequest getRequest(String uri, String baseURI, String nature, String purpose) {
        ResourceRequest req = new ResourceRequest(config, nature, purpose);
        req.setUri(uri);
        req.setBaseURI(baseURI);
        return req;
    }

    /**
     * Lookup a request in the catalog.
     * <p>A catalog lookup never returns the resource, it only searches for a matching catalog entry.</p>
     * @param request the request
     * @return the catalog response
     */
    public CatalogResponse lookup(ResourceRequest request) {
        return catalogResolver.lookup(request);
    }

    /**
     * Resolve a request.
     * <p>Resolves the request first by looking in the catalog, then by attempting to access the (resolved)
     * location. This may follow redirects (if the URI protocol supports them) and may return an
     * open stream.</p>
     * @param request the request
     * @return the response
     */
    public ResourceResponse resolve(ResourceRequest request) {
        CatalogResponse lookup = lookup(request);
        return getResponse(lookup);
    }

    @Override
    public Source resolve(String href, String base) throws TransformerException {
        ResourceRequest request = getRequest(href, base);
        ResourceResponse resp = resolve(request);

        Source source = null;
        if (resp.isResolved()) {
            source = new ResolverSAXSource(resp);
            source.setSystemId(resp.getResolvedURI().toString());
        }

        return source;
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        final ResourceRequest request;
        if (type == null || "http://www.w3.org/TR/REC-xml".equals(type)) {
            logger.log(AbstractLogger.REQUEST, "resolveResource: XML: %s (baseURI: %s, publicId: %s)",
                    systemId, baseURI, publicId);
            request = getRequest(systemId, baseURI, ResolverConstants.DTD_NATURE, ResolverConstants.VALIDATION_PURPOSE);
            request.setPublicId(publicId);
        } else {
            logger.log(AbstractLogger.REQUEST, "resolveResource: %s, %s (namespace: %s, baseURI: %s, publicId: %s)",
                    type, systemId, namespaceURI, baseURI, publicId);

            String purpose = null;
            // If it looks like it's going to be used for validation, ...
            if (ResolverConstants.NATURE_XML_SCHEMA.equals(type)
                    || ResolverConstants.NATURE_XML_SCHEMA_1_1.equals(type)
                    || ResolverConstants.NATURE_RELAX_NG.equals(type)) {
                purpose = ResolverConstants.PURPOSE_SCHEMA_VALIDATION;
            }

            request = getRequest(systemId, baseURI, type, purpose);
            request.setPublicId(publicId);
        }

        CatalogResponse lookup = lookup(request);
        if (!lookup.found && systemId == null) {
            return null;
        }

        ResourceResponse resp = resolve(request);

        LSInput input = null;
        if (resp != null && resp.isResolved()) {
            input = new ResolverLSInput(resp, publicId);
        }

        return input;
    }

    @Override
    public InputSource getExternalSubset(String name, String baseURI) throws SAXException, IOException {
        ResourceRequest request = getRequest(null, baseURI, ResolverConstants.ANY_NATURE, ResolverConstants.ANY_PURPOSE);
        request.setEntityName(name);
        ResourceResponse resp = resolve(request);

        ResolverInputSource source = null;
        if (resp.isResolved()) {
            source = new ResolverInputSource(resp);
            source.setSystemId(resp.getResolvedURI().toString());
        }

        return source;
    }

    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException, IOException {
        ResourceRequest request = getRequest(systemId, baseURI, ResolverConstants.EXTERNAL_ENTITY_NATURE, ResolverConstants.ANY_PURPOSE);
        request.setEntityName(name);
        request.setPublicId(publicId);
        ResourceResponse resp = resolve(request);

        ResolverInputSource source = null;
        if (resp.isResolved()) {
            source = new ResolverInputSource(resp);
            source.setSystemId(resp.getResolvedURI().toString());
        }

        return source;
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        return resolveEntity(null, publicId, null, systemId);
    }

    @Override
    public Source resolveNamespace(String uri, String nature, String purpose) throws TransformerException {
        ResourceRequest request = getRequest(uri, null, nature, purpose);
        ResourceResponse resp = resolve(request);

        ResolverSAXSource source = null;
        if (resp.isResolved()) {
            source = new ResolverSAXSource(resp);
            source.setSystemId(resp.getResolvedURI().toString());
        }

        return source;
    }

    private ResourceResponse getResponse(CatalogResponse lookup) {
        boolean tryRddl = config.getFeature(ResolverFeature.PARSE_RDDL)
                && lookup.request.getNature() != null && lookup.request.getPurpose() != null;

        try {
            if (tryRddl) {
                if (lookup.found) {
                    lookup = rddlLookup(lookup);
                } else {
                    lookup = rddlLookup(lookup, lookup.request.getAbsoluteURI());
                }
            }

            if (!lookup.found) {
                if (config.getFeature(ResolverFeature.ALWAYS_RESOLVE)) {
                    return getUnresolvedResponse(lookup);
                } else {
                    return new ResourceResponse(lookup.request);
                }
            }

            return resource(lookup.request, lookup.uri);
        } catch (URISyntaxException | IOException ex) {
            boolean throwExceptions = config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS);
            if (throwExceptions) {
                logger.log(AbstractLogger.ERROR, ex.getMessage());
                throw new IllegalArgumentException(ex);
            }
            return new ResourceResponse(lookup.request);
        }
    }

    private ResourceResponse getUnresolvedResponse(CatalogResponse lookup) {
        try {
            URI uri = lookup.request.getAbsoluteURI();
            return resource(lookup.request, uri);
        } catch (URISyntaxException | IOException ex) {
            boolean throwExceptions = config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS);
            // IllegalArgumentException occurs if the (unresolved) URI is not absolute, for example.
            logger.log(AbstractLogger.ERROR, "Resolution failed: %s: %s", lookup.uri, ex.getMessage());
            if (throwExceptions) {
                throw new IllegalArgumentException(ex);
            }

            return new ResourceResponse(lookup.request);
        }
    }

    protected ResourceResponse resource(ResourceRequest request, URI responseURI) throws URISyntaxException, IOException {
        if (responseURI == null) {
            return new ResourceResponse(request);
        }

        boolean mask = config.getFeature(ResolverFeature.MASK_JAR_URIS);
        URI showResolvedURI = responseURI;
        if (mask && ("jar".equals(showResolvedURI.getScheme()) || "classpath".equals(showResolvedURI.getScheme()))) {
            showResolvedURI = request.getAbsoluteURI();
        }

        InputStream inputStream = null;

        if ("data".equals(responseURI.getScheme())) {
            // This is a little bit crude; see RFC 2397
            String contentType = null;
            // Can't use URI accessors because they percent decode the string incorrectly.
            String path = responseURI.toString().substring(5);
            int pos = path.indexOf(",");
            if (pos >= 0) {
                String mediatype = path.substring(0, pos);
                String data = path.substring(pos + 1);
                if (mediatype.endsWith(";base64")) {
                    // Base64 decode it
                    inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
                    contentType = mediatype.substring(0, mediatype.length() - 7);
                } else {
                    // URL decode it
                    String charset = "UTF-8";
                    pos = mediatype.indexOf(";charset=");
                    if (pos > 0) {
                        charset = mediatype.substring(pos + 9);
                        pos = charset.indexOf(";");
                        if (pos >= 0) {
                            charset = charset.substring(0, pos);
                        }
                    }
                    try {
                        data = URLDecoder.decode(data, charset);
                    } catch (UnsupportedEncodingException ex) {
                        throw new IllegalArgumentException(ex);
                    }
                    inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
                    contentType = mediatype.isEmpty() ? null : mediatype;
                }

                ResourceResponse resp = new ResourceResponse(request, showResolvedURI, responseURI);
                resp.setInputStream(inputStream);
                resp.setContentType(contentType);
                return resp;
            } else {
                throw new URISyntaxException(responseURI.toString(), "Comma separator missing");
            }
        }

        if ("classpath".equals(responseURI.getScheme())) {
            String path = responseURI.getSchemeSpecificPart();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            // The URI class throws exceptions if you attempt to manipulate
            // classpath: URIs. Fair enough, given their ad hoc invention
            // by the Spring framework. We're going to cheat a little bit here
            // and replace the classpath: URI with the actual URI of the resource
            // found (if one is found). That means downstream processes will
            // have a "useful" URI. It still might not work, due to class loaders and
            // such, but at least it won't immediately blow up.
            URL rsrc = config.getFeature(ResolverFeature.CLASSLOADER).getResource(path);
            if (rsrc == null) {
                throw new IOException("Not found: " + responseURI.toString());
            } else {
                inputStream = rsrc.openStream();
                ResourceResponse resp = new ResourceResponse(request, showResolvedURI, responseURI);
                resp.setInputStream(inputStream);
                return resp;
            }
        }

        URLConnection conn = openConnection(responseURI.toURL(), request.followRedirects());
        ResourceResponse resp = new ResourceResponse(request, showResolvedURI, responseURI);
        resp.setInputStream(conn.getInputStream());
        resp.setHeaders(conn.getHeaderFields());
        resp.setEncoding(conn.getContentEncoding());
        if (conn instanceof HttpURLConnection) {
            // Only get the content type from an HTTP connection. The other types,
            // notably JarURLConnection do an *abysmal* job at guessing.
            // JarURLConnection says any file that begins "<" is text/html.
            resp.setContentType(conn.getContentType());
            resp.setStatusCode(((HttpURLConnection) conn).getResponseCode());
        } else {
            resp.setStatusCode(200); // lie for files and other kinds of connections
        }
        return resp;
    }

    private CatalogResponse rddlLookup(CatalogResponse lookup) {
        return rddlLookup(lookup, lookup.uri);
    }

    private CatalogResponse rddlLookup(CatalogResponse lookup, URI resolved) {
        String nature = lookup.request.getNature();
        String purpose = lookup.request.getPurpose();

        if (resolved == null || nature == null || purpose == null
                || !config.getFeature(ResolverFeature.PARSE_RDDL)) {
            return lookup;
        }

        URI rddl = null;
        rddl = checkRddl(resolved, nature, purpose);

        if (rddl == null) {
            return lookup;
        }

        final CatalogResponse resp;
        ResourceRequest rddlRequest = new ResourceRequest(config, ResolverConstants.ANY_NATURE, ResolverConstants.ANY_PURPOSE);
        rddlRequest.setUri(rddl.toString());
        rddlRequest.setBaseURI(resolved.toString());
        resp = catalogResolver.lookup(rddlRequest);
        if (!resp.found) {
            logger.log(AbstractLogger.RESPONSE, "RDDL %s: %s", resolved, rddl);
            return new CatalogResponse(lookup.request, rddl);
        }
        logger.log(AbstractLogger.RESPONSE, "RDDL %s: %s", resolved, resp.uri);

        return resp;
    }

    private URI checkRddl(URI uri, String nature, String purpose) {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setValidating(false);
            spf.setXIncludeAware(false);

            URLConnection conn = openConnection(uri.toURL(), true);
            String contentType = conn.getContentType();

            if (contentType != null
                    && (contentType.startsWith("text/html")
                    || contentType.startsWith("application/html+xml"))) {
                InputSource source = new InputSource(conn.getInputStream());
                RddlQuery handler = new RddlQuery(conn.getURL().toURI(), nature, purpose);
                SAXParser parser = spf.newSAXParser();
                parser.parse(source, handler);
                return handler.href();
            } else {
                return null;
            }
        } catch (ParserConfigurationException | SAXException | IOException | URISyntaxException | IllegalArgumentException ex) {
            logger.log(AbstractLogger.ERROR, "RDDL parse failed: %s: %s",
                    uri, ex.getMessage());
            return null;
        }
    }

    protected ResolvedResource openConnection(String uri, String baseURI, boolean asEntity, boolean followRedirects) throws IOException {
        try {
            URI absuri = baseURI == null ? URIUtils.cwd() : new URI(baseURI);
            absuri = absuri.resolve(uri);
            return openConnection(absuri, asEntity, followRedirects);
        } catch (IllegalArgumentException ex) {
            if (config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS)) {
                throw ex;
            }
            return null;
        } catch (URISyntaxException ex) {
            if (config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS)) {
                throw new IOException(ex);
            }
            return null;
        }
    }

    protected ResolvedResource openConnection(URI originalURI, boolean asEntity, boolean followRedirects) throws IOException {
        boolean throwExceptions = config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS);

        HashSet<URI> seen = new HashSet<>();
        int count = FOLLOW_REDIRECT_LIMIT;

        URI absoluteURI = originalURI;
        URLConnection connection = null;
        boolean done = false;
        int code = 200;

        final boolean mergeHttps = config.getFeature(ResolverFeature.MERGE_HTTPS);
        final String accessList;
        if (asEntity) {
            accessList = config.getFeature(ResolverFeature.ACCESS_EXTERNAL_ENTITY);
        } else {
            accessList = config.getFeature(ResolverFeature.ACCESS_EXTERNAL_DOCUMENT);
        }

        while (!done) {
            if (URIUtils.forbidAccess(accessList, absoluteURI.toString(), mergeHttps)) {
                if (asEntity) {
                    logger.log(AbstractLogger.REQUEST, "resolveEntity, access denied: null");
                } else {
                    logger.log(AbstractLogger.REQUEST, "resolveURI, access denied: null");
                }
                return null;
            }

            if (seen.contains(absoluteURI)) {
                if (throwExceptions) {
                    throw new IOException("Redirect loop on " + absoluteURI);
                }
                return null;
            }

            if (count <= 0) {
                if (throwExceptions) {
                    throw new IOException("Too many redirects on " + absoluteURI);
                }
                return null;
            }
            seen.add(absoluteURI);
            count--;

            try {
                connection = absoluteURI.toURL().openConnection();
                connection.connect();
            } catch (Exception ex) {
                if (throwExceptions) {
                    throw ex;
                }
                return null;
            }

            done = !(connection instanceof HttpURLConnection);
            if (!done) {
                HttpURLConnection conn = (HttpURLConnection) connection;
                code = conn.getResponseCode();
                if (code >= 300 && code < 400 && followRedirects) {
                    String loc = conn.getHeaderField("location");
                    absoluteURI = absoluteURI.resolve(loc);
                } else {
                    done = true;
                }
            }
        }

        ResolvedResourceImpl rsrc = new ResolvedResourceImpl(originalURI, absoluteURI, connection.getInputStream(), code, connection.getHeaderFields());
        return rsrc;
    }

    private URLConnection openConnection(URL href, boolean followRedirects) throws IOException {
        URLConnection conn = href.openConnection();
        String location = conn.getHeaderField("location");
        if (followRedirects && location != null) {
            HashSet<String> seenLocations = new HashSet<>();
            while (location != null) {
                if (seenLocations.contains(location)) {
                    throw new IOException("Loop in location redirects");
                }

                if (seenLocations.size() > FOLLOW_REDIRECT_LIMIT) {
                    throw new IOException("Too many redirects");
                }

                seenLocations.add(location);
                URL loc = new URL(location);
                conn = loc.openConnection();
                location = conn.getHeaderField("location");
            }
        }
        return conn;
    }

    private static class RddlQuery extends DefaultHandler {
        private final String nature;
        private final String purpose;
        private URI found = null;
        private final Stack<URI> baseUriStack = new Stack<>();

        public RddlQuery(URI baseURI, String nature, String purpose) {
            this.nature = nature;
            this.purpose = purpose;
            baseUriStack.push(baseURI);
        }

        public URI href() {
            return found;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (ResolverConstants.HTML_NS.equals(uri) && attributes.getValue("", "base") != null) {
                baseUriStack.push(baseUriStack.peek().resolve(attributes.getValue("", "base")));
            } else if (attributes.getValue(ResolverConstants.XML_NS, "base") != null) {
                baseUriStack.push(baseUriStack.peek().resolve(attributes.getValue(ResolverConstants.XML_NS, "base")));
            } else {
                baseUriStack.push(baseUriStack.peek());
            }

            if (found == null && ResolverConstants.RDDL_NS.equals(uri) && "resource".equals(localName)) {
                String rnature = attributes.getValue(ResolverConstants.XLINK_NS, "role");
                String rpurpose = attributes.getValue(ResolverConstants.XLINK_NS, "arcrole");
                String href = attributes.getValue(ResolverConstants.XLINK_NS, "href");
                if (nature.equals(rnature) && purpose.equals(rpurpose) && href != null) {
                    found = baseUriStack.peek().resolve(href);
                }
            }
        }

        @Override
        public void endElement (String uri, String localName, String qName) throws SAXException {
            baseUriStack.pop();
        }
    }
}
