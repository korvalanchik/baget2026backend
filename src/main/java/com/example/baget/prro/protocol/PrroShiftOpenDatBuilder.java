package com.example.baget.prro.protocol;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** Builds the unsigned DAT portion of a PRRO shift-opening service document. */
public final class PrroShiftOpenDatBuilder {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss", Locale.ROOT);

    /**
     * Returns only DAT. This is not a complete Check.check_sign payload:
     * RQ/MAC envelope construction, previous-Check hashing, QES signing and
     * DPS submission are deliberately outside this builder.
     */
    public String build(ShiftOpenDocument document) {
        Objects.requireNonNull(document, "document");
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartElement("DAT");
            xml.writeAttribute("FN", document.fiscalNumber());
            xml.writeAttribute("TN", document.taxpayerNumber());
            // The DPS opening-shift example explicitly sends an empty ZN.
            xml.writeAttribute("ZN", "");
            xml.writeAttribute("DI", Long.toString(document.packetId()));
            xml.writeAttribute("V", "1");
            xml.writeStartElement("C");
            xml.writeAttribute("T", "108");
            xml.writeEndElement();
            xml.writeStartElement("TS");
            xml.writeCharacters(TIMESTAMP.format(document.timestamp()));
            xml.writeEndElement();
            xml.writeEndElement();
            xml.flush();
            xml.close();
            return output.toString();
        } catch (XMLStreamException ex) {
            throw new IllegalStateException("Unable to build PRRO shift-open DAT XML", ex);
        }
    }

    public record ShiftOpenDocument(String fiscalNumber, String taxpayerNumber,
                                    long packetId, LocalDateTime timestamp) {
        public ShiftOpenDocument {
            fiscalNumber = requireText(fiscalNumber, "fiscalNumber");
            taxpayerNumber = requireText(taxpayerNumber, "taxpayerNumber");
            if (packetId < 0) {
                throw new IllegalArgumentException("packetId must not be negative");
            }
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
