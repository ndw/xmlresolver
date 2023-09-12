package org.xmlresolver;

import org.apache.xerces.impl.XMLEntityDescription;
import org.apache.xerces.impl.xs.XSDDescription;
import org.apache.xerces.util.SAXInputSource;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.grammars.XMLDTDDescription;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.xml.sax.InputSource;
import org.xmlresolver.sources.ResolverInputSource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * An extension of the {@link Resolver} that implements Xerces {@link org.apache.xerces.xni.parser.XMLEntityResolver XMLEntityResolver}.
 *
 * <p>This is a separate class so that the dependency on Xerces can remain optional. You must have
 * Xerces on your classpath to load this class, obviously. You must also explicitly configure
 * the underlying {@link org.xml.sax.XMLReader XMLReader} to use this resolver:</p>
 * <pre>xmlReader.setProperty("http://apache.org/xml/properties/internal/entity-resolver", resolver);</pre>
 * <p>This API relies on the input to the {@link #resolveEntity(XMLEntityDescription)} method to
 * work out what to resolve. If this API is in use, Xerces doesn't use any of the other resolver APIs.</p>
 */
public class XercesResolver extends Resolver implements org.apache.xerces.xni.parser.XMLEntityResolver {
    // Hat tip to Adam Retter for identifying this API and pointing me in the right direction.

    /**
     * Create a new XercesResolver.
     */
    public XercesResolver() {
        super();
    }

    /**
     * Create a new XercesResolver with a particular configuration.
     * @param config the configuration
     */
    public XercesResolver(XMLResolverConfiguration config) {
        super(config);
    }

    /**
     * Create a new XercesResolver with a particular catalog resolver.
     * @param resolver the catalog resolver
     */
    public XercesResolver(CatalogResolver resolver) {
        super(resolver);
    }

    /**
     * Resolve an entity.
     *
     * <p>How the resolver functions depends on what subclass of {@link XMLResourceIdentifier} is passed
     * in:</p>
     * <dl>
     *     <dt>{@link XMLDTDDescription}</dt>
     *     <dd>Identifies an attept to load a DTD.</dd>
     *     <dt>{@link XMLEntityDescription}</dt>
     *     <dd>Identifies an attept to load an entity (a parameter or general entity in the internal
     *     or external subset).</dd>
     *     <dt>{@link XSDDescription}</dt>
     *     <dd>Identifies an attept to load an XML Schema.</dd>
     * </dl>
     * <p>If some other class is passed in, the method does its best. But if there are other possibilities,
     * the code should really be extended to handle them explicitly.</p>
     *
     * <p>For Schema lookup, it's worth noting that the behavior is slightly different depending on
     * whether or not a "system identifier" is provided for the schema. This can occur in two different ways:
     * if the document author provides a schema location hint, that is provided as the system identifier for
     * the schema. If the schema being resolved is being included or imported into another schema, then the
     * href of the xs:import or xs:include is the "system identifier".</p>
     * <p>If a schema location is provided, the method attempt to resolve it as a URI. In this case, it
     * will not lookup the schema with its namespace. This is to prevent the case where an included or imported
     * module is 404. If that fell back to using the namespace, you would create a loop.</p>
     * <p>If the schema location is not provided, the namespace resolver API is used to find the schema.</p>
     *
     * @param resId the resource identifier
     * @return a resolved source, or null if none was found
     */
    @Override
    public XMLInputSource resolveEntity(XMLResourceIdentifier resId) {
        // Xerces seems to call this API for all resolution. Let's see if we can work out what they're
        // looking for...
        if (resId instanceof XMLDTDDescription) {
            return resolveDTD((XMLDTDDescription) resId);
        } else if (resId instanceof XMLEntityDescription) {
            return resolveEntity((XMLEntityDescription) resId);
        } else if (resId instanceof XSDDescription) {
            return resolveSchema((XSDDescription) resId);
        }

        // Well whadda we do now?

        String publicId = resId.getPublicId();
        String systemId = resId.getLiteralSystemId();
        String baseURI = resId.getBaseSystemId();
        String namespace = resId.getNamespace();

        ResourceRequest request = null;
        ResolvedResource rsrc = null;
        // If the namespace isn't null, we've gone past the doctype declaration, so it's not an entity.
        // Otherwise, if publicId or systemId aren't null, try resolving an entity.
        if (namespace == null) {
            request = getRequest(systemId, baseURI, ResolverConstants.ANY_NATURE, ResolverConstants.ANY_PURPOSE);
            request.setPublicId(publicId);
            rsrc = resolve(request);
            if (!rsrc.isResolved()) {
                request = getRequest(resId.getExpandedSystemId(), baseURI, ResolverConstants.ANY_NATURE, ResolverConstants.ANY_PURPOSE);
                request.setPublicId(publicId);
                rsrc = resolve(request);
            }
        }

        if (rsrc == null || !rsrc.isResolved()) {
            request = getRequest(namespace, resId.getBaseSystemId(), ResolverConstants.NATURE_XML_SCHEMA, ResolverConstants.PURPOSE_SCHEMA_VALIDATION);
            rsrc = resolve(request);
        }

        if (rsrc == null || !rsrc.isResolved()) {
            rsrc = safeOpenConnection(systemId, baseURI, true);
        }

        SAXInputSource source = null;
        if (rsrc != null && rsrc.isResolved()) {
            source = new SAXInputSource(new ResolverInputSource(rsrc));
        }

        return source;
    }

    private ResolvedResource safeOpenConnection(String systemId, String baseURI, boolean asEntity) {
        // This is "safe" in the weird sense that it doesn't throw a checked exception
        if (systemId != null && config.getFeature(ResolverFeature.ALWAYS_RESOLVE)) {
            try {
                return openConnection(systemId, baseURI, asEntity, true);
            } catch (IOException err) {
                // What am I supposed to do about this now?
            }
        }
        return null;
    }

    private XMLInputSource resolveDTD(XMLDTDDescription resId) {
        ResourceRequest request = getRequest(resId.getLiteralSystemId(), resId.getBaseSystemId(),
                ResolverConstants.DTD_NATURE, ResolverConstants.VALIDATION_PURPOSE);
        request.setEntityName(resId.getRootName());
        request.setPublicId(resId.getPublicId());
        ResolvedResource rsrc = resolve(request);
        if (!rsrc.isResolved()) {
            request = getRequest(resId.getExpandedSystemId(), resId.getBaseSystemId(),
                    ResolverConstants.DTD_NATURE, ResolverConstants.VALIDATION_PURPOSE);
            request.setEntityName(resId.getRootName());
            request.setPublicId(resId.getPublicId());
            rsrc = resolve(request);
        }
        if (!rsrc.isResolved()) {
            rsrc = safeOpenConnection(resId.getLiteralSystemId(), resId.getBaseSystemId(), true);
        }
        XMLInputSource source = null;
        if (rsrc.isResolved()) {
            source = new SAXInputSource(new ResolverInputSource(rsrc));
        }
        return source;
    }

    private XMLInputSource resolveEntity(XMLEntityDescription resId) {
        String name = resId.getEntityName();
        if (name.startsWith("%") || name.startsWith("&")) {
            // Oh, please. The [expletive]?
            name = name.substring(1);
        }
        ResourceRequest request = getRequest(resId.getLiteralSystemId(), resId.getBaseSystemId(),
                ResolverConstants.EXTERNAL_ENTITY_NATURE, ResolverConstants.ANY_PURPOSE);
        request.setEntityName(name);
        request.setPublicId(resId.getPublicId());
        ResolvedResource rsrc = resolve(request);
        if (!rsrc.isResolved()) {
            request = getRequest(resId.getExpandedSystemId(), resId.getBaseSystemId(),
                    ResolverConstants.EXTERNAL_ENTITY_NATURE, ResolverConstants.ANY_PURPOSE);
            request.setEntityName(name);
            request.setPublicId(resId.getPublicId());
            rsrc = resolve(request);
        }
        if (rsrc == null) {
            rsrc = safeOpenConnection(resId.getLiteralSystemId(), resId.getBaseSystemId(), true);
        }
        return rsrc == null ? null : new SAXInputSource(new ResolverInputSource(rsrc));
    }

    private XMLInputSource resolveSchema(XSDDescription resId) {
        ResourceRequest request = null;
        ResolvedResource rsrc = null;

        if (resId.getLiteralSystemId() != null) {
            // If there's a "system identifier" then there's either been a schema location
            // hint of some sort or this is an xsd:include. Try to resolve the URI.
            request = getRequest(resId.getLiteralSystemId(), resId.getBaseSystemId());
        } else {
            // We don't want to do namespace resolution if there was a hint because
            // that would take us "back to the top" if some xs:include or xs:import
            // was 404.
            request = getRequest(resId.getNamespace(), resId.getBaseSystemId(), ResolverConstants.NATURE_XML_SCHEMA, ResolverConstants.PURPOSE_SCHEMA_VALIDATION);
        }
        rsrc = resolve(request);

        if (!rsrc.isResolved()) {
            rsrc = safeOpenConnection(resId.getLiteralSystemId(), resId.getBaseSystemId(), false);
        }

        if (rsrc.isResolved()) {
            InputSource source = new ResolverInputSource(rsrc);
            source.setSystemId(rsrc.getLocalURI().toString());
            return new SAXInputSource(source);
        }

        return null;
    }
}
