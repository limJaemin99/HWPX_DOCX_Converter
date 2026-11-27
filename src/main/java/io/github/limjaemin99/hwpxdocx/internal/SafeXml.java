package io.github.limjaemin99.hwpxdocx.internal;

import io.github.limjaemin99.hwpxdocx.ConvertException;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 라이브러리의 모든 XML 읽기·쓰기가 거치는 곳. 외부 참조(외부 엔티티, 외부 DTD, XInclude)를 읽지 않고
 * DOCTYPE이 있으면 거부한다. 폐쇄망에서 멈추지 않게 하고 XXE도 막는다.
 *
 * <p>부르는 쪽 클래스패스에 다른 XML 구현이 있어도 JDK 내장 구현을 이름으로 골라 쓴다.
 * 내장 구현이 없는 JVM이면 기본 구현에 같은 설정을 걸고, 하나라도 받아들이지 않으면 오류로 멈춘다.
 * 팩토리는 스레드에 안전하지 않아 부를 때마다 새로 만든다.
 */
public final class SafeXml {

    static final String JDK_DOM = "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";
    static final String JDK_SAX = "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl";
    static final String JDK_TRANSFORMER = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private SafeXml() {
    }

    /** 네임스페이스를 읽는 DOM. {@code name}은 오류 문구에 넣을 이름(패키지 안 경로 등). */
    public static Document parse(InputStream in, String name) throws ConvertException {
        try {
            return newDocumentBuilder().parse(new InputSource(in));
        } catch (SAXException e) {
            throw new ConvertException("XML을 읽을 수 없음: " + name + " (" + e.getMessage() + ")", e);
        } catch (IOException e) {
            throw new ConvertException("XML을 읽을 수 없음: " + name, e);
        }
    }

    public static DocumentBuilder newDocumentBuilder() throws ConvertException {
        DocumentBuilderFactory f;
        try {
            f = DocumentBuilderFactory.newInstance(JDK_DOM, null);
        } catch (RuntimeException e) {
            f = DocumentBuilderFactory.newInstance();
        }
        configure(f);
        try {
            DocumentBuilder b = f.newDocumentBuilder();
            b.setEntityResolver(REFUSE_ENTITY);
            b.setErrorHandler(THROW_ERRORS);
            return b;
        } catch (ParserConfigurationException e) {
            throw unsupported(f, e);
        }
    }

    static void configure(DocumentBuilderFactory f) throws ConvertException {
        f.setNamespaceAware(true);
        f.setValidating(false);
        f.setExpandEntityReferences(false);
        try {
            f.setXIncludeAware(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature(DISALLOW_DOCTYPE, true);
            f.setFeature(EXTERNAL_GENERAL, false);
            f.setFeature(EXTERNAL_PARAMETER, false);
            f.setFeature(LOAD_EXTERNAL_DTD, false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (ParserConfigurationException | RuntimeException e) {
            throw unsupported(f, e);
        }
    }

    public static XMLReader newXmlReader() throws ConvertException {
        SAXParserFactory f;
        try {
            f = SAXParserFactory.newInstance(JDK_SAX, null);
        } catch (RuntimeException e) {
            f = SAXParserFactory.newInstance();
        }
        f.setNamespaceAware(true);
        f.setValidating(false);
        try {
            f.setXIncludeAware(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature(DISALLOW_DOCTYPE, true);
            f.setFeature(EXTERNAL_GENERAL, false);
            f.setFeature(EXTERNAL_PARAMETER, false);
            f.setFeature(LOAD_EXTERNAL_DTD, false);
            XMLReader r = f.newSAXParser().getXMLReader();
            r.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            r.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            r.setEntityResolver(REFUSE_ENTITY);
            r.setErrorHandler(THROW_ERRORS);
            return r;
        } catch (ParserConfigurationException | SAXException | RuntimeException e) {
            throw unsupported(f, e);
        }
    }

    /**
     * StAX 읽기. JDK 8에는 내장 구현을 이름으로 고르는 공개 방법이 없어 기본 구현을 쓰고,
     * 규격 필수 속성 두 개를 걸어 확인한다. DTD 이벤트가 나오면 예외.
     */
    public static XMLStreamReader newStreamReader(InputStream in, String name) throws ConvertException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        try {
            f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            f.setXMLResolver(REFUSE_STREAM_ENTITY);
        } catch (RuntimeException e) {
            throw unsupported(f, e);
        }
        if (!Boolean.FALSE.equals(f.getProperty(XMLInputFactory.SUPPORT_DTD))
                || !Boolean.FALSE.equals(f.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES))) {
            throw unsupported(f, null);
        }
        try {
            return new NoDtdReader(f.createXMLStreamReader(in), name);
        } catch (XMLStreamException e) {
            throw new ConvertException("XML을 읽을 수 없음: " + name + " (" + e.getMessage() + ")", e);
        }
    }

    /** DOM을 UTF-8로 쓴다. {@code standalone="yes"}가 필요하면 쓰기 전에 {@code doc.setXmlStandalone(true)}. */
    public static void write(Document doc, OutputStream out) throws ConvertException {
        try {
            Transformer t = newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            // JDK 8은 Document의 standalone 값을 선언에 옮기지 않음
            if (doc.getXmlStandalone()) {
                t.setOutputProperty(OutputKeys.STANDALONE, "yes");
            }
            t.transform(new DOMSource(doc), new StreamResult(out));
        } catch (TransformerException e) {
            throw new ConvertException("XML을 쓸 수 없음 (" + e.getMessage() + ")", e);
        }
    }

    public static Transformer newTransformer() throws ConvertException {
        TransformerFactory f;
        try {
            f = TransformerFactory.newInstance(JDK_TRANSFORMER, null);
        } catch (RuntimeException e) {
            f = TransformerFactory.newInstance();
        }
        try {
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            return f.newTransformer();
        } catch (TransformerConfigurationException | RuntimeException e) {
            throw unsupported(f, e);
        }
    }

    private static ConvertException unsupported(Object factory, Exception cause) {
        return new ConvertException("XML 구현 " + factory.getClass().getName()
                + "이(가) 외부 참조 끄기 설정을 받아들이지 않음", cause);
    }

    private static final EntityResolver REFUSE_ENTITY = new EntityResolver() {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
            throw new SAXException("외부 참조를 읽지 않음: " + systemId);
        }
    };

    private static final XMLResolver REFUSE_STREAM_ENTITY = new XMLResolver() {
        @Override
        public Object resolveEntity(String publicId, String systemId, String baseUri, String namespace)
                throws XMLStreamException {
            throw new XMLStreamException("외부 참조를 읽지 않음: " + systemId);
        }
    };

    // 기본 오류 처리기는 표준 오류에 찍고 넘어가므로 바로 던짐
    private static final ErrorHandler THROW_ERRORS = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };

    private static final class NoDtdReader extends StreamReaderDelegate {

        private final String name;

        NoDtdReader(XMLStreamReader reader, String name) {
            super(reader);
            this.name = name;
        }

        @Override
        public int next() throws XMLStreamException {
            int event = super.next();
            if (event == XMLStreamConstants.DTD) {
                throw new XMLStreamException("DOCTYPE이 있는 XML은 읽지 않음: " + name);
            }
            return event;
        }

        // 부모의 nextTag는 위 next()를 거치지 않으므로 다시 씀
        @Override
        public int nextTag() throws XMLStreamException {
            int event = next();
            while ((event == XMLStreamConstants.CHARACTERS && isWhiteSpace())
                    || event == XMLStreamConstants.SPACE
                    || event == XMLStreamConstants.COMMENT
                    || event == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                event = next();
            }
            if (event != XMLStreamConstants.START_ELEMENT && event != XMLStreamConstants.END_ELEMENT) {
                throw new XMLStreamException("시작·끝 태그가 와야 함: " + name, getLocation());
            }
            return event;
        }
    }
}
