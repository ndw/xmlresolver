package org.xmlresolver;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class Issue31Test {
    private Resolver maskingResolver;
    private Resolver nonMaskingResolver;
    private String exp;

    @Before
    public void setUp() {
        URL rsrc = getClass().getClassLoader().getResource("path/catalog.xml");
        assertNotNull(rsrc);

        exp = rsrc.toString();

        String catalog = "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>\n" +
                "  <system systemId='urn:oasis:names:tc:dita:rng:topic.rng'\n" +
                "          uri='" + exp + "'/>\n" +
                "  <system systemId='https://example.com/topic.rng'\n" +
                "          uri='" + exp + "'/>\n" +
                "</catalog>\n";

        final XMLResolverConfiguration configuration = new XMLResolverConfiguration();
        configuration.setFeature(ResolverFeature.MASK_JAR_URIS, true);
        InputStream is = new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8));
        configuration.addCatalog(URI.create("http://xmlresolver.org/test"), new InputSource(is));
        maskingResolver = new Resolver(configuration);

        final XMLResolverConfiguration nonMaskingConfiguration = new XMLResolverConfiguration();
        nonMaskingConfiguration.setFeature(ResolverFeature.MASK_JAR_URIS, false);
        is = new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8));
        nonMaskingConfiguration.addCatalog(URI.create("http://xmlresolver.org/test"), new InputSource(is));
        nonMaskingResolver = new Resolver(nonMaskingConfiguration);
    }

    @Test
    public void urnUnMasked() throws IOException, SAXException {
        final InputSource act = nonMaskingResolver.resolveEntity(null, null, "/", "urn:oasis:names:tc:dita:rng:topic.rng");
        assertEquals(exp, act.getSystemId());
    }

    @Test
    public void urnMasked() throws IOException, SAXException {
        final InputSource act = maskingResolver.resolveEntity(null, null, "/", "urn:oasis:names:tc:dita:rng:topic.rng");
        assertEquals("urn:oasis:names:tc:dita:rng:topic.rng", act.getSystemId());
    }

    @Test
    public void httpsUnMasked() throws IOException, SAXException {
        final InputSource act = nonMaskingResolver.resolveEntity(null, null, "/", "https://example.com/topic.rng");
        assertEquals(exp, act.getSystemId());
    }

    @Test
    public void httpsMasked() throws IOException, SAXException {
        final InputSource act = maskingResolver.resolveEntity(null, null, "/", "https://example.com/topic.rng");
        assertEquals("https://example.com/topic.rng", act.getSystemId());
    }
}