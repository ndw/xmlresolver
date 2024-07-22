package org.xmlresolver;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlresolver.tools.ResolvingXMLReader;
import org.xmlresolver.utils.PublicId;
import org.xmlresolver.utils.URIUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Issue0184Test {
    public static final String catalog = "src/test/resources/empty.xml";

    XMLResolverConfiguration config = null;
    Resolver resolver = null;

    @Before
    public void setup() {
        config = new XMLResolverConfiguration(catalog);
        resolver = new Resolver(config);
    }

    @Test
    public void parserTest() {
        try {
            ResolvingXMLReader reader = new ResolvingXMLReader(resolver);
            String filename = "src/test/iss0184/src/SBBVT0T-Deployment-Flat-mod.xml";
            InputSource source = new InputSource(filename);
            reader.parse(source);
        } catch (IOException | SAXException ex) {
            fail();
        }
    }


}
