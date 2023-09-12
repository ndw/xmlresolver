package org.xmlresolver;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xmlresolver.utils.URIUtils;

import java.io.File;
import java.net.URI;

import static org.junit.Assert.*;

public class RddlTest extends CacheManager {
    public static final String catalog = "src/test/resources/docker.xml";
    public static final URI catloc = URIUtils.cwd().resolve(catalog);
    private static final String relativeCacheDir = "build/rddl-cache";

    XMLResolverConfiguration config = null;
    CatalogResolver resolver = null;

    @BeforeClass
    public static void setupCache() {
        // Only do this once for the whole test suite.
        // On Windows, if this is run @Before, it sometimes runs afoul of documents
        // that haven't been closed yet and can't be removed.
        clearCache(relativeCacheDir);
    }

    @Before
    public void setup() {
        config = new XMLResolverConfiguration(catalog);
        File cache = new File(URIUtils.cwd().getPath() + relativeCacheDir);
        resolver = new CatalogResolver(config);

        // Make sure the Docker container is running where we expect.
        ResourceConnection conn = new ResourceConnection(config, "http://localhost:8222/docs/sample/sample.dtd", true);
        assertEquals(200, conn.getStatusCode());
    }

    @Test
    public void cacheTest() {
        // Not yet cached
        ResolvedResource dtd = resolver.resolveNamespace("http://localhost:8222/docs/sample",
                null,
                "http://www.isi.edu/in-notes/iana/assignments/media-types/application/xml-dtd",
                "http://www.rddl.org/purposes#validation");

        assertNotNull(dtd);
        assertEquals("application/xml-dtd", dtd.getContentType());

        // Should now be cached
       dtd = resolver.resolveNamespace("http://localhost:8222/docs/sample",
                null,
                "http://www.isi.edu/in-notes/iana/assignments/media-types/application/xml-dtd",
                "http://www.rddl.org/purposes#validation");
        assertTrue(dtd.isResolved());
        assertEquals("application/xml-dtd", dtd.getContentType());
    }

    @Test
    public void xsdTest() {
        resolver.getConfiguration().setFeature(ResolverFeature.PARSE_RDDL, true);
        ResolvedResource xsd = resolver.resolveNamespace("http://localhost:8222/docs/sample",
                null,
                "http://www.w3.org/2001/XMLSchema",
                "http://www.rddl.org/purposes#schema-validation");
        assertTrue(xsd.isResolved());
        assertEquals("application/xml", xsd.getContentType());
    }

    @Test
    public void xslTest() {
        resolver.getConfiguration().setFeature(ResolverFeature.PARSE_RDDL, true);
        ResolvedResource xsl = resolver.resolveNamespace("http://localhost:8222/docs/sample",
                null,
                "http://www.w3.org/1999/XSL/Transform",
                "http://www.rddl.org/purposes#transformation");
        assertTrue(xsl.isResolved());
        assertEquals("application/xml", xsl.getContentType());
    }

    @Test
    public void xslTestBaseURI() {
        resolver.getConfiguration().setFeature(ResolverFeature.PARSE_RDDL, true);
        ResolvedResource xsl = resolver.resolveNamespace("sample",
                "http://localhost:8222/docs/",
                "http://www.w3.org/1999/XSL/Transform",
                "http://www.rddl.org/purposes#transformation");
        assertTrue(xsl.isResolved());
        assertEquals("application/xml", xsl.getContentType());
    }

    @Test
    public void xsdTestNoRddl() {
        resolver.getConfiguration().setFeature(ResolverFeature.PARSE_RDDL, false);
        resolver.getConfiguration().setFeature(ResolverFeature.ALWAYS_RESOLVE, true);
        ResolvedResource xsd = resolver.resolveNamespace("http://localhost:8222/docs/sample",
                null,
                "http://www.w3.org/2001/XMLSchema",
                "http://www.rddl.org/purposes#schema-validation");
        assertTrue(xsd.isResolved());
        assertEquals("http://localhost:8222/docs/sample", xsd.getResolvedURI().toString());

        resolver.getConfiguration().setFeature(ResolverFeature.ALWAYS_RESOLVE, false);
        xsd = resolver.resolveNamespace("http://localhost:8222/docs/sample",
                null,
                "http://www.w3.org/2001/XMLSchema",
                "http://www.rddl.org/purposes#schema-validation");
        assertFalse(xsd.isResolved());
    }

    @Test
    public void xslTestNoRddl() {
        resolver.getConfiguration().setFeature(ResolverFeature.PARSE_RDDL, false);
        resolver.getConfiguration().setFeature(ResolverFeature.ALWAYS_RESOLVE, true);
        ResolvedResource xsl = resolver.resolveNamespace("http://localhost:8222/docs/sample",
                null,
                "http://www.w3.org/1999/XSL/Transform",
                "http://www.rddl.org/purposes#transformation");
        assertTrue(xsl.isResolved());
        assertEquals("http://localhost:8222/docs/sample", xsl.getResolvedURI().toString());

        resolver.getConfiguration().setFeature(ResolverFeature.ALWAYS_RESOLVE, false);
        xsl = resolver.resolveNamespace("http://localhost:8222/docs/sample",
                null,
                "http://www.w3.org/1999/XSL/Transform",
                "http://www.rddl.org/purposes#transformation");
        assertFalse(xsl.isResolved());
    }

    @Ignore
    public void xmlTest() {
        // This test is ignored because getting the XSD file from the W3C server takes ten seconds
        // and the test doesn't really prove anything anyway.
        resolver.getConfiguration().setFeature(ResolverFeature.PARSE_RDDL, true);
        ResolvedResource xsd = resolver.resolveNamespace("http://www.w3.org/2001/xml.xsd",
                null,
                "http://www.w3.org/2001/XMLSchema",
                "http://www.rddl.org/purposes#schema-validation");
        assertFalse(xsd.isResolved());
    }

    @Test
    public void xmlTestResolved() {
        // This test gets the xml.xsd file from the catalog, so it runs quickly and proves
        // that we parse the resolved resource, not the original URI.
        XMLResolverConfiguration config = new XMLResolverConfiguration("src/test/resources/catalog.xml");
        config.setFeature(ResolverFeature.PARSE_RDDL, true);
        CatalogResolver resolver = new CatalogResolver(config);

        ResolvedResource xsd = resolver.resolveNamespace("http://www.w3.org/XML/1998/namespace",
                null,
                "http://www.w3.org/2001/XMLSchema",
                "http://www.rddl.org/purposes#schema-validation");
        assertTrue(xsd.isResolved());
        assertTrue(xsd.getResolvedURI().toString().endsWith("/xml.xsd"));
    }
}

