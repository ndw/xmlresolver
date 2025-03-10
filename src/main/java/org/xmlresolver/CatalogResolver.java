package org.xmlresolver;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlresolver.cache.CacheEntry;
import org.xmlresolver.cache.ResourceCache;
import org.xmlresolver.logging.AbstractLogger;
import org.xmlresolver.logging.ResolverLogger;
import org.xmlresolver.utils.URIUtils;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/** The CatalogResolver returns resolved resources in a uniform way.
 *
 * The various resolver APIs want different return results. This class wraps those up in a
 * uniform interface so that the details about resolution are more readily available.g
 */

public class CatalogResolver implements ResourceResolver {
    public static final int FOLLOW_REDIRECT_LIMIT = 100;
    private final ResolverLogger logger;
    private XMLResolverConfiguration config = null;
    private ResourceCache cache = null;

    /**
     * Creates a new instance of ResourceResolver using a default configuration.
     */
    public CatalogResolver() {
        this(new XMLResolverConfiguration());
    }

    /**
     * Creates a new instance of ResourceResolver with the specified resolver configuration.
     *
     * @param conf The {@link XMLResolverConfiguration} to use.
     * @throws NullPointerException if the configuration is null.
     */
    public CatalogResolver(XMLResolverConfiguration conf) {
        config = conf;
        cache = conf.getFeature(ResolverFeature.CACHE);
        logger = conf.getFeature(ResolverFeature.RESOLVER_LOGGER);
    }

    /** Returns the {@link XMLResolverConfiguration} used by this ResourceResolver.
     *
     * @return The configuration.
     */
    public XMLResolverConfiguration getConfiguration() {
        return config;
    }

    /** Resolve a URI.
     *
     * <p>The catalog is interrogated for the specified <code>href</code>. If it is found, its mapping is returned.
     * Otherwise, the <code>href</code> is made absolute with respect to the specified
     * <code>base</code> and the catalog is interrogated for the resulting absolute URI. If it is found,
     * its mapping is returned.</p>
     *
     * @param href The URI of the resource
     * @param baseURI The base URI against which <code>href</code> should be made absolute, if necessary.
     * @throws IllegalArgumentException if the THROW_URI_EXCEPTIONS feature is true and an exception occurs resolving a URI.
     * @return The resolved resource or null if no resolution was possible.
     */
    @Override
    public ResolvedResource resolveURI(String href, String baseURI) {
        logger.log(AbstractLogger.REQUEST, "resolveURI: %s (base URI: %s)", href, baseURI);

        boolean throwExceptions = config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS);

        if (href == null || "".equals(href)) {
            href = baseURI;
            baseURI = null;
            if (href == null || "".equals(href)) {
                logger.log(AbstractLogger.RESPONSE, "resolveURI: null");
                return null;
            }
        }

        try {
            URI uri = new URI(href);
            if (uri.isAbsolute()) {
                if (forbidAccess(config.getFeature(ResolverFeature.ACCESS_EXTERNAL_DOCUMENT), href)) {
                    logger.log(AbstractLogger.REQUEST, "resolveURI (access denied): " + href);
                    throw new IllegalArgumentException("Resource protocol access denied: " + href);
                }
            }
        } catch (URISyntaxException ex) {
            // nevermind
        }

        CatalogManager catalog = config.getFeature(ResolverFeature.CATALOG_MANAGER);
        URI resolved = catalog.lookupURI(href);
        if (resolved != null) {
            logger.log(AbstractLogger.RESPONSE, "resolveURI: %s", resolved);
            return resource(href, resolved, cache.cachedUri(resolved));
        }

        String absolute = href;
        if (baseURI != null) {
            try {
                absolute = new URI(baseURI).resolve(href).toString();
                if (!href.equals(absolute)) {
                    if (forbidAccess(config.getFeature(ResolverFeature.ACCESS_EXTERNAL_DOCUMENT), absolute)) {
                        logger.log(AbstractLogger.REQUEST, "resolveURI (access denied): " + absolute);
                        throw new IllegalArgumentException("Resource protocol access denied: " + absolute);
                    }
                    resolved = catalog.lookupURI(absolute);
                    if (resolved != null) {
                        logger.log(AbstractLogger.RESPONSE, "resolveURI: %s", resolved);
                        return resource(absolute, resolved, cache.cachedUri(resolved));
                    }
                }
            } catch (URISyntaxException ex) {
                logger.log(AbstractLogger.ERROR, "URI syntax exception: %s (base URI: %s)", href, baseURI);
                if (throwExceptions) {
                    throw new IllegalArgumentException(ex);
                }
                logger.log(AbstractLogger.RESPONSE, "resolveURI: null");
                return null;

            } catch (IllegalArgumentException ex) {
                logger.log(AbstractLogger.ERROR, "URI argument exception: %s (base URI: %s)", href, baseURI);
                if (throwExceptions) {
                    throw ex;
                }
                logger.log(AbstractLogger.RESPONSE, "resolveURI: null");
                return null;
            }
        }

        try {
            // There's no point attempting to cache data: URIs.
            if (absolute.startsWith("data:")) {
                URI absuri = new URI(absolute);
                return uncachedResource(absuri, absuri);
            }

            if (cache.cacheURI(absolute)) {
                URI absuri = new URI(absolute);
                logger.log(AbstractLogger.RESPONSE, "resolveURI: cached %s", absolute);
                return resource(absolute, absuri, cache.cachedUri(absuri));
            } else {
                logger.log(AbstractLogger.RESPONSE, "resolveURI: null");
                return null;
            }
        } catch (URISyntaxException use) {
            logger.log(AbstractLogger.ERROR, "URI syntax exception: %s", absolute);
            if (throwExceptions) {
                throw new IllegalArgumentException(use);
            }
        } catch (IllegalArgumentException ex) {
            logger.log(AbstractLogger.ERROR, "URI argument exception: %s", absolute);
            if (throwExceptions) {
                throw ex;
            }
        } catch (IOException ex) {
            logger.log(AbstractLogger.ERROR, "I/O exception: %s", absolute);
            if (throwExceptions) {
                throw new IllegalArgumentException(ex);
            }
        }

        logger.log(AbstractLogger.RESPONSE, "resolveURI: null");
        return null;
    }

    /** Resolve external identifiers and other entity-like resources.
     *
     * <p>If the systemId is null and the baseURI is not, the systemId is taken to be the baseURI
     * and the baseURI is treated as if it were null.
     *
     * <p>If name, publicId and systemId are all null, null is returned.</p>
     *
     * <p>If systemId or publicId are not null, the method attempts to resolve an external identifier with
     * the specified external identifier and the (possibly null) name and publicId. (Because public
     * identifiers can be encoded in URNs, it's possible for this method to receive a publicId and
     * not a systemId, even in XML systems.)</p>
     *
     * <p>If name is not null, but systemId is, name is assumed to be document type name and
     * the method attempts to resolve an external identifier that matches. A <code>doctype</code> catalog
     * entry, for example.</p>
     *
     * <p>If the systemId is relative, resolution fails, and baseURI is not null, the systemId
     * is made absolute with respect to the baseURI and resolution is attempted a second time.</p>
     *
     * @param name The possibly null entity (or document type) name.
     * @param publicId The possibly null public identifier.
     * @param systemId The possibly relative system identifier to resolve.
     * @param baseURI The possibly null base URI to use if systemId is relative and not resolved.
     * @throws IllegalArgumentException if the THROW_URI_EXCEPTIONS feature is true and an exception occurs resolving a URI.
     * @return The resolved resource or null if no resolution was possible.
     */
    @Override
    public ResolvedResource resolveEntity(String name, String publicId, String systemId, String baseURI) {
        if (name == null && publicId == null && systemId == null && baseURI == null) {
            logger.log(AbstractLogger.REQUEST, "resolveEntity: null");
            return null;
        }

        if (systemId != null) {
            if (config.getFeature(ResolverFeature.FIX_WINDOWS_SYSTEM_IDENTIFIERS)) {
                systemId = systemId.replace("\\", "/");
                // The base URI may be wrong too...
                if (baseURI != null) {
                    baseURI = baseURI.replace("\\", "/");
                }
            }

            try {
                URI uri = new URI(systemId);
                if (uri.isAbsolute()) {
                    if (forbidAccess(config.getFeature(ResolverFeature.ACCESS_EXTERNAL_ENTITY), systemId)) {
                        logger.log(AbstractLogger.REQUEST, "resolveEntity (access denied): " + systemId);
                        throw new IllegalArgumentException("Resource protocol access denied: " + systemId);
                    }
                }
            } catch (URISyntaxException ex) {
                // nevermind
            }
        }

        if (name != null && publicId == null && systemId == null) {
            logger.log(AbstractLogger.REQUEST, "resolveEntity: name: %s (%s)", name, baseURI);
            return resolveDoctype(name, baseURI);
        }

        if (name != null) {
            logger.log(AbstractLogger.REQUEST, "resolveEntity: %s %s (baseURI: %s, publicId: %s)",
                    name, systemId, baseURI, publicId);
        } else {
            logger.log(AbstractLogger.REQUEST, "resolveEntity: %s (baseURI: %s, publicId: %s)",
                    systemId, baseURI, publicId);
        }

        URI absSystem = null;

        boolean throwExceptions = config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS);
        CatalogManager catalog = config.getFeature(ResolverFeature.CATALOG_MANAGER);
        ResolvedResourceImpl result = null;
        URI resolved = catalog.lookupEntity(name, systemId, publicId);
        if (resolved == null && systemId != null && config.getFeature(ResolverFeature.URI_FOR_SYSTEM)) {
            resolved = catalog.lookupURI(systemId);
        }
        if (resolved != null) {
            result = resource(systemId, resolved, cache.cachedSystem(resolved, publicId));
        } else {
            try {
                if (systemId != null) {
                    absSystem = new URI(systemId);
                }
                if (baseURI != null) {
                    absSystem = new URI(baseURI);
                    if (systemId != null) {
                        absSystem = absSystem.resolve(systemId);
                    }

                    if (forbidAccess(config.getFeature(ResolverFeature.ACCESS_EXTERNAL_ENTITY), absSystem.toString())) {
                        logger.log(AbstractLogger.REQUEST, "resolveEntity (access denied): " + absSystem);
                        throw new IllegalArgumentException("Resource protocol access denied: " + absSystem);
                    }

                    resolved = catalog.lookupEntity(name, absSystem.toString(), publicId);
                    if (resolved == null && config.getFeature(ResolverFeature.URI_FOR_SYSTEM)) {
                        resolved = catalog.lookupURI(absSystem.toString());
                    }
                    if (resolved != null) {
                        result = resource(absSystem.toString(), resolved, cache.cachedSystem(resolved, publicId));
                    }
                }
            } catch (URISyntaxException ex) {
                logger.log(AbstractLogger.ERROR, "URI exception: %s (base: %s)", systemId, baseURI);
                if (throwExceptions) {
                    throw new IllegalArgumentException(ex);
                }
                logger.log(AbstractLogger.RESPONSE, "resolveURI: null");
                return null;
            } catch (IllegalArgumentException ex) {
                logger.log(AbstractLogger.ERROR, "URI exception: %s (base: %s)", systemId, baseURI);
                if (throwExceptions) {
                    throw ex;
                }
                logger.log(AbstractLogger.RESPONSE, "resolveURI: null");
                return null;
            }
        }

        if (result != null) {
            logger.log(AbstractLogger.RESPONSE, "resolveEntity: %s", resolved);
            return result;
        }

        if (absSystem == null) {
            logger.log(AbstractLogger.RESPONSE, "resolveEntity: null");
            return null;
        }

        try {
            // There's no point attempting to cache data: URIs.
            if ("data".equals(absSystem.getScheme())) {
                return uncachedResource(absSystem, absSystem);
            }

            if (cache.cacheURI(absSystem.toString())) {
                logger.log(AbstractLogger.RESPONSE, "resolveEntity: cached %s", absSystem);
                return resource(absSystem.toString(), absSystem, cache.cachedSystem(absSystem, publicId));
            }
        } catch (URISyntaxException use) {
            logger.log(AbstractLogger.ERROR, "URI syntax exception: %s", absSystem);
            if (throwExceptions) {
                throw new IllegalArgumentException(use);
            }
        } catch (IllegalArgumentException ex) {
            logger.log(AbstractLogger.ERROR, "URI argument exception: %s", absSystem);
            if (throwExceptions) {
                throw ex;
            }
        } catch (IOException ex) {
            logger.log(AbstractLogger.ERROR, "I/O exception: %s", absSystem);
            if (throwExceptions) {
                throw new IllegalArgumentException(ex);
            }
        }

        logger.log(AbstractLogger.RESPONSE, "resolveEntity: null");
        return null;
    }

    /**
     * Resolve a document type from its name.
     *
     * <p>Searches the catalog for a DOCTYPE entry with a matching name.</p>
     * @param name The doctype name.
     * @param baseURI The base uri. Ignored.
     * @return The resolved resource or null if no resolution was possible.
     */
    private ResolvedResource resolveDoctype(String name, String baseURI) {
        if (baseURI == null) {
            logger.log(AbstractLogger.REQUEST, "resolveDoctype: %s", name);
        } else {
            logger.log(AbstractLogger.REQUEST, "resolveDoctype: %s (%s)", name, baseURI);
            try {
                URI uri = new URI(baseURI);
                if (uri.isAbsolute()) {
                    if (forbidAccess(config.getFeature(ResolverFeature.ACCESS_EXTERNAL_ENTITY), baseURI)) {
                        logger.log(AbstractLogger.REQUEST, "resolveEntity (access denied): " + baseURI);
                        throw new IllegalArgumentException("Resource protocol access denied: " + baseURI);
                    }
                }
            } catch (URISyntaxException ex) {
                // nevermind
            }
        }

        CatalogManager catalog = config.getFeature(ResolverFeature.CATALOG_MANAGER);
        URI resolved = catalog.lookupDoctype(name, null, null);
        if (resolved == null) {
            logger.log(AbstractLogger.RESPONSE, "resolveDoctype: null");
            return null;
        } else {
            logger.log(AbstractLogger.RESPONSE, "resolveDoctype: %s", resolved);
            ResolvedResourceImpl result = resource(null, resolved, cache.cachedSystem(resolved, null));
            return result;
        }
    }

    /** Resolve a Namespace URI.
     *
     * <p>The catalog is interrogated for the specified namespace. If it is found, it is returned.</p>
     *
     * <p>The resolver attempts to parse the resource as an XML document. If it finds RDDL 1.0 markup,
     * it will attempt to locate the resource with the specified nature and purpose. That resource will
     * be downloaded and returned. If it cannot parse the document or cannot find the markup that
     * it is looking for, it will return the resource that represents the namespace URI.</p>
     *
     * @param uri The namespace URI.
     * @param baseURI The base URI of the resource.
     * @param nature The RDDL nature of the resource.
     * @param purpose The RDDL purpose of the resource.
     * @throws IllegalArgumentException if the THROW_URI_EXCEPTIONS feature is true and an exception occurs resolving a URI.
     * @return The resolved resource or null if no resolution was possible.
     */
    @Override
    public ResolvedResource resolveNamespace(String uri, String baseURI, String nature, String purpose) {
        logger.log(AbstractLogger.REQUEST, "resolveNamespace: %s (base: %s, nature: %s, purpose: %s)",
                uri, baseURI, nature, purpose);
        CatalogManager catalog = config.getFeature(ResolverFeature.CATALOG_MANAGER);
        boolean throwExceptions = config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS);

        URI absolute = null;
        try {
            absolute = new URI(uri);
            if (absolute.isAbsolute()) {
                if (forbidAccess(config.getFeature(ResolverFeature.ACCESS_EXTERNAL_DOCUMENT), uri)) {
                    logger.log(AbstractLogger.REQUEST, "resolveNamespace (access denied): " + uri);
                    throw new IllegalArgumentException("Resource protocol access denied: " + uri);
                }
            }
        } catch (URISyntaxException ex) {
            if (throwExceptions) {
                throw new IllegalArgumentException(ex);
            }
        } catch (IllegalArgumentException ex) {
            if (throwExceptions) {
                throw ex;
            }
        }

        URI resolved = catalog.lookupNamespaceURI(uri, nature, purpose);
        if (resolved == null && baseURI != null) {
            try {
                absolute = new URI(baseURI).resolve(uri);
                // If we can make it absolute, we want it to be absolute from here on out
                uri = absolute.toString();

                if (absolute.isAbsolute()) {
                    if (forbidAccess(config.getFeature(ResolverFeature.ACCESS_EXTERNAL_DOCUMENT), uri)) {
                        logger.log(AbstractLogger.REQUEST, "resolveNamespace (access denied): " + uri);
                        throw new IllegalArgumentException("Resource protocol access denied: " + uri);
                    }
                }

                resolved = catalog.lookupNamespaceURI(uri, nature, purpose);
            } catch (URISyntaxException ex) {
                if (throwExceptions) {
                    throw new IllegalArgumentException(ex);
                }
            } catch (IllegalArgumentException ex) {
                if (throwExceptions) {
                    throw ex;
                }
            }
        }

        CacheEntry cached = resolved == null ? null : cache.cachedNamespaceUri(resolved, nature, purpose);

        if (config.getFeature(ResolverFeature.PARSE_RDDL) && nature != null && purpose != null) {
            URI rddl = null;
            if (cached != null) {
                rddl = checkRddl(cached.file.toURI(), nature, purpose, cached.contentType());
                if (rddl != null) {
                    cached = cache.cachedUri(rddl);
                }
            } else {
                try {
                    if (resolved != null) {
                        rddl = checkRddl(resolved, nature, purpose, null);
                    } else {
                        rddl = checkRddl(URIUtils.newURI(uri), nature, purpose, null);
                    }
                } catch (URISyntaxException ex) {
                    logger.log(AbstractLogger.ERROR, "URI syntax exception: " + uri);
                    if (throwExceptions) {
                        throw new IllegalArgumentException(ex);
                    }
                    logger.log(AbstractLogger.RESPONSE, "resolveNamespace: null");
                    return null;
                } catch (IllegalArgumentException ex) {
                    logger.log(AbstractLogger.ERROR, "URI argument exception: " + uri);
                    if (throwExceptions) {
                        throw ex;
                    }
                    logger.log(AbstractLogger.RESPONSE, "resolveNamespace: null");
                    return null;
                }
            }

            if (rddl != null) {
                ResolvedResource local = resolveURI(rddl.toString(), uri);
                if (local != null) {
                    logger.log(AbstractLogger.RESPONSE, "resolveNamespace: %s", local.getLocalURI());
                    return local;
                } else {
                    logger.log(AbstractLogger.RESPONSE, "resolveNamespace: %s", rddl);
                    return resource(uri, rddl, cached);
                }
            }
        }

        if (resolved != null) {
            logger.log(AbstractLogger.RESPONSE, "resolveNamespace: %s", resolved);
            return resource(absolute == null ? null : absolute.toString(),
                    resolved, cache.cachedNamespaceUri(resolved, nature, purpose));
        } else {
            logger.log(AbstractLogger.RESPONSE, "resolveNamespace: null");
            return null;
        }
    }

    private boolean forbidAccess(String allowed, String uri) {
        return URIUtils.forbidAccess(allowed, uri, config.getFeature(ResolverFeature.MERGE_HTTPS));
    }

    protected ResolvedResourceImpl resource(String requestURI, URI responseURI, CacheEntry cached) {
        boolean throwExceptions = config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS);
        try {
            if (cached == null) {
                return uncachedResource(URIUtils.newURI(requestURI), responseURI);
            } else {
                FileInputStream fs = new FileInputStream(cached.file);
                // N.B. We always lie about the local URI when caching. If we didn't, then when
                // the process using the resolver made a relative URI absolute against the local URI
                // of the resource, it would get a URI pointing into the cache which would not work.
                return new ResolvedResourceImpl(responseURI, cached.file.toURI(), fs, cached.contentType());
            }
        } catch (IOException | URISyntaxException ex) {
            // IllegalArgumentException occurs if the (unresolved) URI is not absolute, for example.
            logger.log(AbstractLogger.ERROR, "Resolution failed: %s: %s", responseURI, ex.getMessage());
            if (throwExceptions) {
                throw new IllegalArgumentException(ex);
            }
            return null;
        } catch (IllegalArgumentException ex) {
            // IllegalArgumentException occurs if the (unresolved) URI is not absolute, for example.
            logger.log(AbstractLogger.ERROR, "Resolution failed: %s: %s", responseURI, ex.getMessage());
            if (throwExceptions) {
                throw ex;
            }
            return null;
        }
    }

    protected ResolvedResourceImpl uncachedResource(URI req, URI res) throws IOException, URISyntaxException {
        boolean mask = config.getFeature(ResolverFeature.MASK_JAR_URIS);
        URI showResolvedURI = res;
        if (mask && ("jar".equals(showResolvedURI.getScheme()) || "classpath".equals(showResolvedURI.getScheme()))) {
            showResolvedURI = req;
        }

        InputStream inputStream = null;

        if ("data".equals(res.getScheme())) {
            // This is a little bit crude; see RFC 2397

            String contentType = null;
            // Can't use URI accessors because they percent decode the string incorrectly.
            String path = res.toString().substring(5);
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
                    data = URLDecoder.decode(data, charset);
                    inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
                    contentType = "".equals(mediatype) ? null : mediatype;
                }

                return new ResolvedResourceImpl(showResolvedURI, res, inputStream, contentType);
            } else {
                throw new URISyntaxException(res.toString(), "Comma separator missing");
            }
        }

        if ("classpath".equals(res.getScheme())) {
            String path = res.getSchemeSpecificPart();
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
                throw new IOException("Not found: " + res);
            } else {
                inputStream = rsrc.openStream();
                return new ResolvedResourceImpl(showResolvedURI, res, inputStream, null);
            }
        }

        URLConnection conn = res.toURL().openConnection();
        return new ResolvedResourceImpl(showResolvedURI, res, conn.getInputStream(), conn.getContentType());
    }

    private URI checkRddl(URI uri, String nature, String purpose, String contentType) {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setValidating(false);
            spf.setXIncludeAware(false);

            URLConnection conn = uri.toURL().openConnection();
            String location = conn.getHeaderField("location");

            if (location != null) {
                HashSet<String> seenLocations = new HashSet<>();
                while (location != null) {
                    if (seenLocations.contains(location)) {
                        return null;
                    }

                    if (seenLocations.size() > FOLLOW_REDIRECT_LIMIT) {
                        return null;
                    }

                    seenLocations.add(location);
                    URL loc = new URL(location);
                    conn = loc.openConnection();
                    location = conn.getHeaderField("location");
                }
            }

            Map<String, List<String>> x = conn.getHeaderFields();
            if (contentType == null) {
                contentType = conn.getContentType();
            }
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
