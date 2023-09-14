package org.xmlresolver;

import javax.xml.transform.TransformerException;

/** The CatalogResolver returns resolved resources in a uniform way.
 *
 * The various resolver APIs want different return results. This class wraps those up in a
 * uniform interface so that the details about resolution are more readily available.
 */

public class CatalogResolver implements ResourceResolver {
    private final XMLResolverConfiguration config;
    private final Resolver resolver;

    public CatalogResolver() {
        this.config = new XMLResolverConfiguration();
        this.resolver = new Resolver(config);
    }

    public CatalogResolver(XMLResolverConfiguration conf) {
        this.config = conf;
        this.resolver = new Resolver(conf);
    }

    public XMLResolverConfiguration getConfiguration() {
        return config;
    }

    @Override
    public ResolvedResource resolveURI(String href, String baseURI) {
        ResourceRequest request = resolver.getRequest(href, baseURI);
        ResolvedResource resp = resolver.resolve(request);
        return resp.isResolved() ? resp : null; // backwards compatibility
    }

    @Override
    public ResolvedResource resolveNamespace(String uri, String baseURI, String nature, String purpose) {
        ResourceRequest request = resolver.getRequest(uri, baseURI, nature, purpose);
        ResolvedResource resp = resolver.resolve(request);
        return resp.isResolved() ? resp : null; // backwards compatibility
    }

    @Override
    public ResolvedResource resolveEntity(String name, String publicId, String systemId, String baseURI) {
        ResourceRequest request = resolver.getRequest(systemId, baseURI, ResolverConstants.EXTERNAL_ENTITY_NATURE, ResolverConstants.ANY_PURPOSE);
        request.setEntityName(name);
        request.setPublicId(publicId);
        ResolvedResource resp = resolver.resolve(request);
        return resp.isResolved() ? resp : null; // backwards compatibility
    }
}
