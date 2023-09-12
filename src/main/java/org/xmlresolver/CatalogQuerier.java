package org.xmlresolver;

import org.xmlresolver.logging.AbstractLogger;
import org.xmlresolver.logging.ResolverLogger;
import org.xmlresolver.utils.URIUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Lookup a resource in an XML catalog.
 * <p>The methods in this class only query the catalog(s). They do not attempt to retrieve
 * any resources.</p>
 */
public class CatalogQuerier {
    private final XMLResolverConfiguration config;
    private final ResolverLogger logger;

    /**
     * Construct the querier with a default XML Resolver configuration.
     */
    public CatalogQuerier() {
        this.config = new XMLResolverConfiguration();
        logger = config.getFeature(ResolverFeature.RESOLVER_LOGGER);
    }

    /**
     * Construct the querier with a specific XML Resolver configuration.
     * @param config The XML resolver configuration.
     */
    public CatalogQuerier(XMLResolverConfiguration config) {
        this.config = config;
        logger = config.getFeature(ResolverFeature.RESOLVER_LOGGER);
    }

    /**
     * Return the XML Resolver configuration used by this querier.
     * @return the configuration
     */
    public XMLResolverConfiguration getConfiguration() {
        return config;
    }

    /**
     * Find the catalog entry that satisfies the request and return it.
     * <p>This method always returns a response; the {@code found} field or the {@link CatalogResponse#isResolved()}
     * method can be used to determine if it was successful.</p>
     * @param request the request
     * @return a query response
     */
    public CatalogResponse lookup(ResourceRequest request) {
        String name = request.getEntityName();
        String publicId = request.getPublicId();
        String systemId = request.getSystemId();
        String baseURI = request.getBaseURI();
        String nature = request.getNature();
        String purpose = request.getPurpose();

        final boolean resolveEntity;
        final String kind; // only used for messages
        if (ResolverConstants.EXTERNAL_ENTITY_NATURE.equals(nature)
                || ResolverConstants.DTD_NATURE.equals(nature)) {
            kind = "resolveEntity";
            resolveEntity = true;
        } else {
            kind = "resolveURI";
            resolveEntity = false;
        }

        if (name == null && publicId == null && systemId == null && baseURI == null) {
            logger.log(AbstractLogger.REQUEST, "%s: null", kind);
            return new CatalogResponse(request);
        }

        if (ResolverConstants.DTD_NATURE.equals(nature)
                && name != null && publicId == null && systemId == null) {
            return resolveDoctype(request);
        }

        boolean throwExceptions = config.getFeature(ResolverFeature.THROW_URI_EXCEPTIONS);
        CatalogManager catalog = config.getFeature(ResolverFeature.CATALOG_MANAGER);

        URI systemIdURI = null;
        if (systemId != null) {
            try {
                systemIdURI = new URI(systemId);
                if (systemIdURI.isAbsolute()) {
                    if (forbidAccess(config.getFeature(ResolverFeature.ACCESS_EXTERNAL_ENTITY), systemId)) {
                        logger.log(AbstractLogger.REQUEST, "%s (access denied): null", kind);
                        return new CatalogResponse(request);
                    }
                }
            } catch (URISyntaxException ex) {
                logger.log(AbstractLogger.ERROR, "URI exception: %s", systemId);
                if (throwExceptions) {
                    throw new IllegalArgumentException(ex);
                }
            }
        }

        logger.log(AbstractLogger.REQUEST, "%s: %s%s (baseURI: %s, publicId: %s)",
                kind, (name == null ? "" : name + " "), systemId, baseURI, publicId);

        URI resolved = null;
        if (resolveEntity) {
            resolved = catalog.lookupEntity(name, systemId, publicId);
            if (resolved == null && systemId != null && config.getFeature(ResolverFeature.URI_FOR_SYSTEM)) {
                resolved = catalog.lookupURI(systemId);
            }
        } else {
            if (systemId == null) {
                return new CatalogResponse(request);
            }
            resolved = catalog.lookupNamespaceURI(systemId, nature, purpose);
        }

        if (resolved != null) {
            return new CatalogResponse(request, resolved);
        }

        URI absSystem = null;
        if (systemId != null && systemIdURI != null && systemIdURI.isAbsolute()) {
            absSystem = systemIdURI;
        } else {
            try {
                absSystem = request.getAbsoluteURI();
            } catch (URISyntaxException ex) {
                logger.log(AbstractLogger.ERROR, "URI exception: %s (base: %s)", systemId, baseURI);
                if (throwExceptions) {
                    throw new IllegalArgumentException(ex);
                }
            }
        }

        if (absSystem != null) {
            if (resolveEntity) {
                resolved = catalog.lookupEntity(name, absSystem.toString(), publicId);
                if (resolved == null && config.getFeature(ResolverFeature.URI_FOR_SYSTEM)) {
                    resolved = catalog.lookupURI(absSystem.toString());
                }
            } else {
                resolved = catalog.lookupNamespaceURI(absSystem.toString(), request.getNature(), request.getPurpose());
            }
        }

        return new CatalogResponse(request, resolved);
    }

    private CatalogResponse resolveDoctype(ResourceRequest request) {
        String name = request.getEntityName();
        String baseURI = null;

        try {
            URI uri = request.getAbsoluteURI();
            if (uri != null) {
                baseURI = uri.toString();
            }
        } catch (URISyntaxException ex) {
            // nop;
        }

        if (baseURI == null) {
            logger.log(AbstractLogger.REQUEST, "resolveDoctype: %s", name);
        } else {
            logger.log(AbstractLogger.REQUEST, "resolveDoctype: %s (%s)", name, baseURI);
        }

        CatalogManager catalog = config.getFeature(ResolverFeature.CATALOG_MANAGER);
        URI resolved = catalog.lookupDoctype(name, null, null);
        if (resolved == null) {
            logger.log(AbstractLogger.RESPONSE, "resolveDoctype: null");
            return new CatalogResponse(request);
        } else {
            logger.log(AbstractLogger.RESPONSE, "resolveDoctype: %s", resolved);
            return new CatalogResponse(request, resolved);
        }
    }

    private boolean forbidAccess(String allowed, String uri) {
        return URIUtils.forbidAccess(allowed, uri, config.getFeature(ResolverFeature.MERGE_HTTPS));
    }
}
