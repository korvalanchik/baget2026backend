package com.example.baget.prro.protocol;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds unsigned XML packets for a general-purpose PRRO.
 *
 * <p>The returned document is the DAT portion only. This class deliberately
 * does not calculate MAC, previous-document hashes, QES signatures, or the
 * gRPC Check.check_sign payload. Do not submit its output directly to DPS.</p>
 *
 * <p>Amounts are accepted in UAH and serialized in kopecks; quantities are
 * accepted in natural units and serialized in thousandths, as required by
 * the DPS information-transfer protocol version 2.2.3, revision 3.1.</p>
 */
public final class PrroXmlDocumentBuilder {

    private static final DateTimeFormatter PACKET_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss", Locale.ROOT);
    private static final DateTimeFormatter TAX_DATE =
            DateTimeFormatter.BASIC_ISO_DATE;
    private static final String CASH_PAYMENT_NAME = "Готівка";

    /**
     * Builds a CHK payload for a general-purpose sale or return.
     * The caller is responsible for assigning a unique packetId per PRRO.
     */
    public String buildCheck(CheckDocument document) {
        Objects.requireNonNull(document, "document");
        validateCheckOperations(document);

        return writeXml(xml -> {
            writePacketStart(xml, document.header());
            xml.writeStartElement("C");
            attribute(xml, "T", document.receiptType().protocolCode());

            for (SaleItem item : document.items()) {
                writeSaleItem(xml, item);
            }
            for (Payment payment : document.payments()) {
                writePayment(xml, payment);
            }
            writeReceiptClosing(xml, document.header(), document.closing());

            xml.writeEndElement(); // C
            writePacketTimestamp(xml, document.header().timestamp());
            xml.writeEndElement(); // DAT
        });
    }

    /**
     * Builds an unsigned ZREPORT payload for a general-purpose PRRO.
     * The caller is responsible for assigning a unique packetId per PRRO.
     */
    public String buildZReport(ZReportDocument document) {
        Objects.requireNonNull(document, "document");

        return writeXml(xml -> {
            writePacketStart(xml, document.header());
            xml.writeStartElement("Z");
            attribute(xml, "NO", document.reportNumber());

            for (TaxSummary summary : document.taxSummaries()) {
                writeTaxSummary(xml, summary);
            }
            for (PaymentTurnover turnover : document.paymentTurnovers()) {
                writePaymentTurnover(xml, turnover);
            }
            for (CashMovementSummary movement : document.cashMovements()) {
                writeCashMovement(xml, movement);
            }

            xml.writeEmptyElement("NC");
            attribute(xml, "NI", document.checkCounts().salesCount());
            attribute(xml, "NO", document.checkCounts().returnCount());

            xml.writeEndElement(); // Z
            writePacketTimestamp(xml, document.header().timestamp());
            xml.writeEndElement(); // DAT
        });
    }

    /**
     * Converts a UAH amount to the integer representation required by the
     * protocol (UAH multiplied by 100). No rounding is performed.
     */
    public static long moneyToMinorUnits(BigDecimal amountUah) {
        return scaledInteger(amountUah, 2, "amountUah");
    }

    /**
     * Converts a quantity to the integer representation required by the
     * protocol (quantity multiplied by 1000). No rounding is performed.
     */
    public static long quantityToThousandths(BigDecimal quantity) {
        return scaledInteger(quantity, 3, "quantity");
    }

    private static void validateCheckOperations(CheckDocument document) {
        int maxOperationNumber = 0;
        for (SaleItem item : document.items()) {
            maxOperationNumber = Math.max(maxOperationNumber, item.operationNumber());
        }
        for (Payment payment : document.payments()) {
            maxOperationNumber = Math.max(maxOperationNumber, payment.operationNumber());
        }
        if (document.closing().operationNumber() <= maxOperationNumber) {
            throw new IllegalArgumentException(
                    "Closing operation number must follow all item and payment operation numbers");
        }
    }

    private static void writePacketStart(XMLStreamWriter xml, PacketHeader header)
            throws XMLStreamException {
        xml.writeStartElement("DAT");
        attribute(xml, "FN", header.fiscalNumber());
        attribute(xml, "TN", header.taxpayerNumber());
        attribute(xml, "ZN", header.serialNumber());
        attribute(xml, "DI", header.packetId());
        // This builder supports only the general-purpose PRRO document format.
        attribute(xml, "DT", 0);
        attribute(xml, "V", 1);
    }

    private static void writePacketTimestamp(XMLStreamWriter xml, LocalDateTime timestamp)
            throws XMLStreamException {
        xml.writeStartElement("TS");
        xml.writeCharacters(PACKET_TIMESTAMP.format(timestamp));
        xml.writeEndElement();
    }

    private static void writeSaleItem(XMLStreamWriter xml, SaleItem item)
            throws XMLStreamException {
        xml.writeStartElement("P");
        attribute(xml, "N", item.operationNumber());
        attributeIfPresent(xml, "C", item.productCode());
        attribute(xml, "NM", item.name());
        attribute(xml, "SM", moneyToMinorUnits(item.amountUah()));

        BigDecimal quantity = item.quantity();
        boolean defaultQuantity = quantity == null || quantity.compareTo(BigDecimal.ONE) == 0;
        if (!defaultQuantity) {
            attribute(xml, "Q", quantityToThousandths(quantity));
            attribute(xml, "PRC", moneyToMinorUnits(item.unitPriceUah()));
        }

        attributeIfPresent(xml, "CD", item.barcode());
        attribute(xml, "TX", item.taxCode());
        xml.writeEndElement();
    }

    private static void writePayment(XMLStreamWriter xml, Payment payment)
            throws XMLStreamException {
        xml.writeStartElement("M");
        attribute(xml, "N", payment.operationNumber());
        attribute(xml, "T", payment.type().protocolCode());
        attribute(xml, "NM", payment.type() == PaymentType.CASH
                ? CASH_PAYMENT_NAME : payment.name());
        attribute(xml, "SM", moneyToMinorUnits(payment.amountUah()));
        xml.writeEndElement();
    }

    private static void writeReceiptClosing(XMLStreamWriter xml, PacketHeader header,
                                            ReceiptClosing closing)
            throws XMLStreamException {
        xml.writeStartElement("E");
        attribute(xml, "N", closing.operationNumber());
        attribute(xml, "NO", closing.fiscalReceiptNumber());
        attribute(xml, "SM", moneyToMinorUnits(closing.totalUah()));
        attributeIfPresent(xml, "SE", closing.amountWithoutTaxUah() == null
                ? null : moneyToMinorUnits(closing.amountWithoutTaxUah()));
        attribute(xml, "FN", header.fiscalNumber());
        attribute(xml, "TS", PACKET_TIMESTAMP.format(header.timestamp()));
        attributeIfPresent(xml, "CS", closing.cashierNumber());

        if (closing.taxes().size() == 1) {
            writeTaxAttributes(xml, closing.taxes().get(0));
        } else if (closing.taxes().size() > 1) {
            for (TaxBreakdown tax : closing.taxes()) {
                xml.writeStartElement("TX");
                writeTaxAttributes(xml, tax);
                xml.writeEndElement();
            }
        }
        xml.writeEndElement();
    }

    private static void writeTaxAttributes(XMLStreamWriter xml, TaxBreakdown tax)
            throws XMLStreamException {
        attribute(xml, "TX", tax.taxCode());
        rateAttribute(xml, "TXPR", tax.ratePercent());
        moneyAttribute(xml, "TXSM", tax.taxAmountUah());
        rateAttribute(xml, "DTPR", tax.additionalRatePercent());
        moneyAttribute(xml, "DTSM", tax.additionalTaxUah());
        attributeIfPresent(xml, "TXTY", tax.taxIncluded());
        attributeIfPresent(xml, "TXAL", tax.taxAlgorithm());
    }

    private static void writeTaxSummary(XMLStreamWriter xml, TaxSummary summary)
            throws XMLStreamException {
        xml.writeEmptyElement("TXS");
        attribute(xml, "TX", summary.taxCode());
        attributeIfPresent(xml, "TS", summary.taxEffectiveDate() == null
                ? null : TAX_DATE.format(summary.taxEffectiveDate()));
        rateAttribute(xml, "TXPR", summary.ratePercent());
        moneyAttribute(xml, "TXI", summary.receivedTaxUah());
        moneyAttribute(xml, "TXO", summary.paidTaxUah());
        rateAttribute(xml, "DTPR", summary.additionalRatePercent());
        moneyAttribute(xml, "DTI", summary.additionalReceivedTaxUah());
        moneyAttribute(xml, "DTO", summary.additionalPaidTaxUah());
        attributeIfPresent(xml, "TXTY", summary.taxIncluded());
        attributeIfPresent(xml, "TXAL", summary.taxAlgorithm());
        moneyAttribute(xml, "SMI", summary.receivedTurnoverUah());
        moneyAttribute(xml, "SMO", summary.paidTurnoverUah());
    }

    private static void writePaymentTurnover(XMLStreamWriter xml, PaymentTurnover turnover)
            throws XMLStreamException {
        xml.writeEmptyElement("M");
        writePaymentIdentity(xml, turnover.type(), turnover.name());
        moneyAttribute(xml, "SMI", turnover.receivedUah());
        moneyAttribute(xml, "SMO", turnover.paidUah());
    }

    private static void writeCashMovement(XMLStreamWriter xml, CashMovementSummary movement)
            throws XMLStreamException {
        xml.writeEmptyElement("IO");
        writePaymentIdentity(xml, movement.type(), movement.name());
        moneyAttribute(xml, "SMI", movement.receivedUah());
        moneyAttribute(xml, "SMO", movement.paidUah());
    }

    private static void writePaymentIdentity(XMLStreamWriter xml, PaymentType type, String name)
            throws XMLStreamException {
        if (type != null) {
            attribute(xml, "T", type.protocolCode());
        }
        String actualName = type == PaymentType.CASH ? CASH_PAYMENT_NAME : name;
        attributeIfPresent(xml, "NM", actualName);
    }

    private static void moneyAttribute(XMLStreamWriter xml, String name, BigDecimal value)
            throws XMLStreamException {
        if (value != null) {
            attribute(xml, name, moneyToMinorUnits(value));
        }
    }

    private static void rateAttribute(XMLStreamWriter xml, String name, BigDecimal value)
            throws XMLStreamException {
        if (value != null) {
            attribute(xml, name, formatRate(value));
        }
    }

    private static String formatRate(BigDecimal value) {
        Objects.requireNonNull(value, "rate");
        if (value.signum() < 0 || value.compareTo(new BigDecimal("99.99")) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0.00 and 99.99");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Tax rate must have at most two decimal places", ex);
        }
    }

    private static long scaledInteger(BigDecimal value, int decimalPlaces, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        try {
            return value.movePointRight(decimalPlaces)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    fieldName + " has too many decimal places or exceeds the supported range", ex);
        }
    }

    private static void attribute(XMLStreamWriter xml, String name, Object value)
            throws XMLStreamException {
        xml.writeAttribute(name, String.valueOf(value));
    }

    private static void attributeIfPresent(XMLStreamWriter xml, String name, Object value)
            throws XMLStreamException {
        if (value != null) {
            attribute(xml, name, value);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static BigDecimal requireAmount(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        moneyToMinorUnits(value);
        return value;
    }

    private static void validateTaxFields(String taxCode, BigDecimal ratePercent,
                                          BigDecimal taxAmountUah, BigDecimal additionalRatePercent,
                                          BigDecimal additionalTaxUah, Integer taxIncluded,
                                          Integer taxAlgorithm) {
        requireText(taxCode, "taxCode");
        if (ratePercent != null) {
            formatRate(ratePercent);
        }
        if (additionalRatePercent != null) {
            formatRate(additionalRatePercent);
        }
        validateOptionalAmount(taxAmountUah, "taxAmountUah");
        validateOptionalAmount(additionalTaxUah, "additionalTaxUah");
        if (taxIncluded != null && taxIncluded != 0 && taxIncluded != 1 && taxIncluded != 2) {
            throw new IllegalArgumentException("taxIncluded must be 0, 1, or 2");
        }
        if (taxAlgorithm != null && (taxAlgorithm < 0 || taxAlgorithm > 3)) {
            throw new IllegalArgumentException("taxAlgorithm must be between 0 and 3");
        }
    }

    private static void validateOptionalAmount(BigDecimal value, String fieldName) {
        if (value != null) {
            requireAmount(value, fieldName);
        }
    }

    private static List<TaxBreakdown> immutableTaxes(List<TaxBreakdown> taxes) {
        return taxes == null ? List.of() : List.copyOf(taxes);
    }

    public enum ReceiptType {
        SALE("0"),
        RETURN("1");

        private final String protocolCode;

        ReceiptType(String protocolCode) {
            this.protocolCode = protocolCode;
        }

        public String protocolCode() {
            return protocolCode;
        }
    }

    public enum PaymentType {
        CASH(0),
        OTHER(1),
        PAYMENT_INSTRUMENT(2);

        private final int protocolCode;

        PaymentType(int protocolCode) {
            this.protocolCode = protocolCode;
        }

        public int protocolCode() {
            return protocolCode;
        }
    }

    public record PacketHeader(String fiscalNumber, String taxpayerNumber, String serialNumber,
                               long packetId, LocalDateTime timestamp) {
        public PacketHeader {
            fiscalNumber = requireText(fiscalNumber, "fiscalNumber");
            taxpayerNumber = requireText(taxpayerNumber, "taxpayerNumber");
            serialNumber = requireText(serialNumber, "serialNumber");
            if (packetId < 0) {
                throw new IllegalArgumentException("packetId must not be negative");
            }
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }

    public record SaleItem(int operationNumber, String productCode, String name,
                           BigDecimal amountUah, BigDecimal quantity, BigDecimal unitPriceUah,
                           String taxCode, String barcode) {
        public SaleItem {
            if (operationNumber < 1) {
                throw new IllegalArgumentException("Sale item operationNumber must be positive");
            }
            name = requireText(name, "item name");
            amountUah = requireAmount(amountUah, "item amount");
            taxCode = requireText(taxCode, "item taxCode");
            if (productCode != null && productCode.isBlank()) {
                productCode = null;
            }
            if (barcode != null && barcode.isBlank()) {
                barcode = null;
            }
            if (quantity != null) {
                if (quantity.signum() <= 0) {
                    throw new IllegalArgumentException("quantity must be positive");
                }
                quantityToThousandths(quantity);
                if (quantity.compareTo(BigDecimal.ONE) != 0) {
                    Objects.requireNonNull(unitPriceUah,
                            "unitPriceUah is required when quantity differs from one");
                    requireAmount(unitPriceUah, "unitPriceUah");
                }
            }
        }
    }

    public record Payment(int operationNumber, PaymentType type, String name, BigDecimal amountUah) {
        public Payment {
            if (operationNumber < 1) {
                throw new IllegalArgumentException("Payment operationNumber must be positive");
            }
            Objects.requireNonNull(type, "payment type");
            amountUah = requireAmount(amountUah, "payment amount");
            if (type == PaymentType.CASH) {
                if (name != null && !name.isBlank() && !CASH_PAYMENT_NAME.equals(name)) {
                    throw new IllegalArgumentException("Cash payments must use the name Готівка");
                }
                name = CASH_PAYMENT_NAME;
            } else {
                name = requireText(name, "payment name");
            }
        }
    }

    public record TaxBreakdown(String taxCode, BigDecimal ratePercent, BigDecimal taxAmountUah,
                               BigDecimal additionalRatePercent, BigDecimal additionalTaxUah,
                               Integer taxIncluded, Integer taxAlgorithm) {
        public TaxBreakdown {
            validateTaxFields(taxCode, ratePercent, taxAmountUah, additionalRatePercent,
                    additionalTaxUah, taxIncluded, taxAlgorithm);
        }
    }

    public record ReceiptClosing(int operationNumber, long fiscalReceiptNumber, BigDecimal totalUah,
                                 BigDecimal amountWithoutTaxUah, Integer cashierNumber,
                                 List<TaxBreakdown> taxes) {
        public ReceiptClosing {
            if (operationNumber < 1) {
                throw new IllegalArgumentException("Closing operationNumber must be positive");
            }
            if (fiscalReceiptNumber < 1) {
                throw new IllegalArgumentException("fiscalReceiptNumber must be positive");
            }
            totalUah = requireAmount(totalUah, "receipt total");
            validateOptionalAmount(amountWithoutTaxUah, "amountWithoutTaxUah");
            if (cashierNumber != null && cashierNumber < 0) {
                throw new IllegalArgumentException("cashierNumber must not be negative");
            }
            taxes = immutableTaxes(taxes);
        }
    }

    public record CheckDocument(PacketHeader header, ReceiptType receiptType, List<SaleItem> items,
                                List<Payment> payments, ReceiptClosing closing) {
        public CheckDocument {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(receiptType, "receiptType");
            items = items == null ? List.of() : List.copyOf(items);
            payments = payments == null ? List.of() : List.copyOf(payments);
            Objects.requireNonNull(closing, "closing");
            if (items.isEmpty()) {
                throw new IllegalArgumentException("A CHK receipt must contain at least one sale item");
            }
            if (payments.isEmpty()) {
                throw new IllegalArgumentException("A CHK receipt must contain at least one payment");
            }
        }
    }

    public record TaxSummary(String taxCode, LocalDate taxEffectiveDate, BigDecimal ratePercent,
                             BigDecimal receivedTaxUah, BigDecimal paidTaxUah,
                             BigDecimal additionalRatePercent, BigDecimal additionalReceivedTaxUah,
                             BigDecimal additionalPaidTaxUah, Integer taxIncluded, Integer taxAlgorithm,
                             BigDecimal receivedTurnoverUah, BigDecimal paidTurnoverUah) {
        public TaxSummary {
            validateTaxFields(taxCode, ratePercent, receivedTaxUah, additionalRatePercent,
                    additionalReceivedTaxUah, taxIncluded, taxAlgorithm);
            validateOptionalAmount(paidTaxUah, "paidTaxUah");
            validateOptionalAmount(additionalPaidTaxUah, "additionalPaidTaxUah");
            validateOptionalAmount(receivedTurnoverUah, "receivedTurnoverUah");
            validateOptionalAmount(paidTurnoverUah, "paidTurnoverUah");
        }
    }

    public record PaymentTurnover(PaymentType type, String name, BigDecimal receivedUah,
                                  BigDecimal paidUah) {
        public PaymentTurnover {
            validateSummaryPaymentIdentity(type, name);
            validateOptionalAmount(receivedUah, "receivedUah");
            validateOptionalAmount(paidUah, "paidUah");
        }
    }

    public record CashMovementSummary(PaymentType type, String name, BigDecimal receivedUah,
                                      BigDecimal paidUah) {
        public CashMovementSummary {
            validateSummaryPaymentIdentity(type, name);
            validateOptionalAmount(receivedUah, "receivedUah");
            validateOptionalAmount(paidUah, "paidUah");
        }
    }

    private static void validateSummaryPaymentIdentity(PaymentType type, String name) {
        if (type == PaymentType.CASH) {
            if (name != null && !name.isBlank() && !CASH_PAYMENT_NAME.equals(name)) {
                throw new IllegalArgumentException("Cash payment summaries must use the name Готівка");
            }
        } else if (type != null && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("A payment name is required for non-cash payment types");
        }
    }

    public record CheckCounts(long salesCount, long returnCount) {
        public CheckCounts {
            if (salesCount < 0 || returnCount < 0) {
                throw new IllegalArgumentException("Check counts must not be negative");
            }
        }
    }

    public record ZReportDocument(PacketHeader header, long reportNumber,
                                  List<TaxSummary> taxSummaries,
                                  List<PaymentTurnover> paymentTurnovers,
                                  List<CashMovementSummary> cashMovements,
                                  CheckCounts checkCounts) {
        public ZReportDocument {
            Objects.requireNonNull(header, "header");
            if (reportNumber < 0) {
                throw new IllegalArgumentException("reportNumber must not be negative");
            }
            taxSummaries = taxSummaries == null ? List.of() : List.copyOf(taxSummaries);
            paymentTurnovers = paymentTurnovers == null ? List.of() : List.copyOf(paymentTurnovers);
            cashMovements = cashMovements == null ? List.of() : List.copyOf(cashMovements);
            Objects.requireNonNull(checkCounts, "checkCounts");
        }
    }

    @FunctionalInterface
    private interface XmlWriterAction {
        void write(XMLStreamWriter xml) throws XMLStreamException;
    }

    private static String writeXml(XmlWriterAction action) {
        StringWriter output = new StringWriter();
        XMLStreamWriter xml = null;
        try {
            xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            action.write(xml);
            xml.flush();
            xml.close();
            return output.toString();
        } catch (XMLStreamException ex) {
            throw new IllegalStateException("Unable to build PRRO XML packet", ex);
        } finally {
            if (xml != null) {
                try {
                    xml.close();
                } catch (XMLStreamException ignored) {
                    // The original write/close failure, if any, is reported above.
                }
            }
        }
    }
}
