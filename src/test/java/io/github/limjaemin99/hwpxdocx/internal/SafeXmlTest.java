package io.github.limjaemin99.hwpxdocx.internal;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import io.github.limjaemin99.hwpxdocx.testkit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertContains;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertEquals;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertFalse;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertThrows;
import static io.github.limjaemin99.hwpxdocx.testkit.Assert.assertTrue;

class SafeXmlTest {

    private static final String NS = "urn:example:a";

    // 법제처 국가법령정보센터 공개 법령 문장 (대한민국헌법 제1조제1항)
    private static final String TEXT = "대한민국은 민주공화국이다.";

    private static InputStream xml(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    /** 외부 참조로 읽히면 안 되는 내용을 담은 임시 파일. */
    private static File secretFile() throws Exception {
        File f = File.createTempFile("safexml", ".txt");
        f.deleteOnExit();
        Files.write(f.toPath(), "SECRET-CONTENT".getBytes(StandardCharsets.US_ASCII));
        return f;
    }

    private static String externalEntityXml() throws Exception {
        return "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x SYSTEM \"" + secretFile().toURI()
                + "\">]><a>&x;</a>";
    }

    private static String parameterEntityXml() throws Exception {
        return "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY % p SYSTEM \"" + secretFile().toURI()
                + "\"> %p;]><a/>";
    }

    // 닫힌 포트라 혹시 접속을 시도해도 바로 실패함. DOCTYPE 단계에서 거부되어야 함
    private static final String EXTERNAL_DTD_XML =
            "<?xml version=\"1.0\"?><!DOCTYPE a SYSTEM \"http://127.0.0.1:9/none.dtd\"><a/>";

    private static final String ENTITY_EXPANSION_XML = "<?xml version=\"1.0\"?><!DOCTYPE a ["
            + "<!ENTITY e \"eeeeeeeeee\"><!ENTITY f \"&e;&e;&e;&e;&e;&e;&e;&e;&e;&e;\">]><a>&f;</a>";

    @Test
    void parsesNormalXmlWithNamespace() throws Exception {
        Document d = SafeXml.parse(xml("<?xml version=\"1.0\" encoding=\"UTF-8\"?><p:a xmlns:p=\"" + NS
                + "\"><p:b>" + TEXT + "</p:b></p:a>"), "test.xml");
        Element root = d.getDocumentElement();
        assertEquals(NS, root.getNamespaceURI());
        assertEquals("a", root.getLocalName());
        assertEquals(TEXT, root.getTextContent());
    }

    @Test
    void domRejectsDoctypeAndExternalReferences() throws Exception {
        for (String s : new String[] {externalEntityXml(), parameterEntityXml(), EXTERNAL_DTD_XML,
                ENTITY_EXPANSION_XML}) {
            ConvertException e = assertThrows(ConvertException.class, () -> SafeXml.parse(xml(s), "bad.xml"));
            assertContains(e.getMessage(), "bad.xml");
            assertFalse(e.getMessage().contains("SECRET-CONTENT"));
        }
    }

    @Test
    void domIgnoresXInclude() throws Exception {
        String s = "<a xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\""
                + secretFile().toURI() + "\" parse=\"text\"/></a>";
        Document d = SafeXml.parse(xml(s), "xi.xml");
        assertEquals("", d.getDocumentElement().getTextContent());
        assertEquals(1, d.getElementsByTagNameNS("http://www.w3.org/2001/XInclude", "include").getLength());
    }

    @Test
    void malformedXmlIsConvertException() {
        assertThrows(ConvertException.class, () -> SafeXml.parse(xml("<a><b></a>"), "broken.xml"));
    }

    @Test
    void saxRejectsDoctypeAndExternalReferences() throws Exception {
        for (String s : new String[] {externalEntityXml(), parameterEntityXml(), EXTERNAL_DTD_XML}) {
            assertThrows(Exception.class, () -> {
                org.xml.sax.XMLReader r = SafeXml.newXmlReader();
                r.setContentHandler(new DefaultHandler());
                r.parse(new InputSource(xml(s)));
            });
        }
        org.xml.sax.XMLReader r = SafeXml.newXmlReader();
        final StringBuilder text = new StringBuilder();
        r.setContentHandler(new DefaultHandler() {
            @Override
            public void characters(char[] ch, int start, int length) {
                text.append(ch, start, length);
            }
        });
        r.parse(new InputSource(xml("<a>" + TEXT + "</a>")));
        assertEquals(TEXT, text.toString());
    }

    @Test
    void staxReadsNormalXml() throws Exception {
        XMLStreamReader r = SafeXml.newStreamReader(xml("<a> <!-- c --> <b>" + TEXT + "</b></a>"), "s.xml");
        assertEquals(XMLStreamConstants.START_ELEMENT, r.nextTag());
        assertEquals("a", r.getLocalName());
        assertEquals(XMLStreamConstants.START_ELEMENT, r.nextTag());
        assertEquals(TEXT, r.getElementText());
        r.close();
    }

    @Test
    void staxRejectsDoctype() throws Exception {
        for (String s : new String[] {externalEntityXml(), parameterEntityXml(), EXTERNAL_DTD_XML,
                ENTITY_EXPANSION_XML}) {
            Exception e = assertThrows(Exception.class, () -> {
                XMLStreamReader r = SafeXml.newStreamReader(xml(s), "bad.xml");
                StringBuilder text = new StringBuilder();
                while (r.hasNext()) {
                    if (r.next() == XMLStreamConstants.CHARACTERS) {
                        text.append(r.getText());
                    }
                }
                throw new AssertionError("거부되지 않음: " + text);
            });
            assertFalse(String.valueOf(e.getMessage()).contains("SECRET-CONTENT"));
        }
    }

    @Test
    void writeRoundTrip() throws Exception {
        Document d = SafeXml.newDocumentBuilder().newDocument();
        d.setXmlStandalone(true);
        Element root = d.createElementNS(NS, "p:a");
        root.setTextContent(TEXT);
        d.appendChild(root);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SafeXml.write(d, out);
        String s = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(s.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\""));
        assertContains(s, "standalone=\"yes\"");
        assertEquals(TEXT, SafeXml.parse(xml(s), "out.xml").getDocumentElement().getTextContent());
    }

    @Test
    void usesJdkParserEvenIfAnotherIsConfigured() throws Exception {
        String key = "javax.xml.parsers.DocumentBuilderFactory";
        String old = System.getProperty(key);
        System.setProperty(key, StubFactory.class.getName());
        try {
            assertTrue(DocumentBuilderFactory.newInstance() instanceof StubFactory);
            assertEquals(TEXT, SafeXml.parse(xml("<a>" + TEXT + "</a>"), "a.xml")
                    .getDocumentElement().getTextContent());
        } finally {
            if (old == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, old);
            }
        }
    }

    @Test
    void factoryRejectingSettingsFailsLoudly() {
        ConvertException e = assertThrows(ConvertException.class, () -> SafeXml.configure(new StubFactory()));
        assertContains(e.getMessage(), StubFactory.class.getName());
    }

    /** 보안 설정을 받아들이지 않는 오래된 구현 흉내. */
    public static final class StubFactory extends DocumentBuilderFactory {

        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
            throw new ParserConfigurationException("stub");
        }

        @Override
        public void setAttribute(String name, Object value) {
            throw new IllegalArgumentException(name);
        }

        @Override
        public Object getAttribute(String name) {
            throw new IllegalArgumentException(name);
        }

        @Override
        public void setFeature(String name, boolean value) throws ParserConfigurationException {
            throw new ParserConfigurationException(name);
        }

        @Override
        public boolean getFeature(String name) throws ParserConfigurationException {
            throw new ParserConfigurationException(name);
        }
    }
}
