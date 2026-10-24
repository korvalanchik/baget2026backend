package com.example.baget.prro.protocol;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Applies the XML packet canonicalization rules described in Appendix A of
 * the DPS information-transfer protocol, version 2.2.3, revision 3.1.
 *
 * <p>This class only canonicalizes the XML representation. It does not create
 * a MAC, calculate the PRRO previous-document hash, create a QES signature,
 * or produce the final signed {@code Check.check_sign} payload.</p>
 */
public final class PrroXmlCanonicalizer {

    /**
     * Returns a canonical XML representation without an XML declaration.
     *
     * <p>Formatting whitespace between child elements is removed, empty
     * elements are expanded to paired start/end tags, attributes are sorted
     * by name, and runs of spaces/tabs in text nodes are collapsed to one
     * space, following the examples in Appendix A.</p>
     *
     * @param xml an XML packet, for example a {@code DAT} packet
     * @return canonical XML for the packet root element
     * @throws IllegalArgumentException if the input is empty, malformed, or
     *                                  uses a document type declaration
     */
    public String canonicalize(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("PRRO XML must not be blank");
        }

        Document document = parseDocument(xml);
        Element root = document.getDocumentElement();
        if (root == null) {
            throw new IllegalArgumentException("PRRO XML must have a root element");
        }

        StringBuilder result = new StringBuilder(xml.length());
        appendElement(root, result);
        return result.toString();
    }

    /**
     * Canonicalizes a packet and encodes its bytes using Windows-1251.
     *
     * <p>The protocol's XML examples declare Windows-1251. This method makes
     * the character encoding explicit for protocol-level MAC/hash test vectors;
     * it does not calculate a MAC, add a previous-document hash, create a QES
     * signature, or build the final gRPC {@code Check.check_sign} payload.</p>
     *
     * <p>Characters that cannot be represented in Windows-1251 are rejected
     * instead of being silently replaced, because replacement would change the
     * bytes used by any later cryptographic operation.</p>
     *
     * @param xml an XML packet, for example a {@code DAT} packet
     * @return canonical packet bytes encoded as Windows-1251, without an XML declaration
     * @throws IllegalArgumentException if the XML is invalid or contains
     *                                  characters not representable in Windows-1251
     */
    public byte[] canonicalizeToWindows1251(String xml) {
        String canonical = canonicalize(xml);
        CharsetEncoder encoder = Charset.forName("windows-1251").newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer encoded = encoder.encode(CharBuffer.wrap(canonical));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException(
                    "Canonical PRRO XML contains characters not representable in Windows-1251", ex);
        }
    }

    private static Document parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });

            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new IllegalArgumentException("Invalid or unsupported PRRO XML", ex);
        }
    }

    private static void appendElement(Element element, StringBuilder result) {
        result.append('<').append(element.getTagName());

        List<Attr> attributes = new ArrayList<>();
        NamedNodeMap attributeMap = element.getAttributes();
        for (int i = 0; i < attributeMap.getLength(); i++) {
            Node node = attributeMap.item(i);
            if (node instanceof Attr attribute) {
                attributes.add(attribute);
            }
        }
        attributes.sort(Comparator.comparing(Attr::getName));

        for (Attr attribute : attributes) {
            result.append(' ')
                    .append(attribute.getName())
                    .append("=\"")
                    .append(escapeAttribute(attribute.getValue()))
                    .append('"');
        }
        result.append('>');

        NodeList children = element.getChildNodes();
        boolean hasElementChild = hasElementChild(children);
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE -> appendElement((Element) child, result);
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> appendText(child, hasElementChild, result);
                case Node.COMMENT_NODE, Node.PROCESSING_INSTRUCTION_NODE ->
                        throw new IllegalArgumentException(
                                "Comments and processing instructions are not part of the supported PRRO packet format");
                case Node.ENTITY_REFERENCE_NODE ->
                        throw new IllegalArgumentException("Entity references are not supported in PRRO XML");
                default -> {
                    // Other DOM node types do not contribute to the packet representation.
                }
            }
        }

        result.append("</").append(element.getTagName()).append('>');
    }

    private static boolean hasElementChild(NodeList children) {
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    private static void appendText(Node node, boolean parentHasElementChild, StringBuilder result) {
        String value = Objects.requireNonNullElse(node.getNodeValue(), "");
        if (parentHasElementChild && value.isBlank()) {
            // Appendix A removes indentation between XML tags.
            return;
        }

        // Appendix A's examples normalize runs of spaces/tabs in tag values.
        value = value.replaceAll("[ \\t]+", " ");
        result.append(escapeText(value));
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace("\"", "&quot;")
                .replace("\t", "&#9;")
                .replace("\n", "&#10;")
                .replace("\r", "&#13;");
    }

    private static String escapeText(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace("]]>", "]]&gt;");
    }
}
