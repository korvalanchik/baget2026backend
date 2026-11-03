package com.example.baget.prro.protocol;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Derives ZREPORT aggregates only from persisted, fiscalized CHK signing inputs.
 * Unsupported receipt constructs fail closed rather than producing guessed totals.
 */
@Component
public final class PrroZReportAggregator {

    public Aggregates aggregate(List<byte[]> fiscalizedSigningInputs) {
        Objects.requireNonNull(fiscalizedSigningInputs, "fiscalizedSigningInputs");
        Map<String, TaxTotals> taxes = new TreeMap<>();
        Map<PaymentKey, PaymentTotals> payments = new TreeMap<>(Comparator
                .comparingInt(PaymentKey::type).thenComparing(PaymentKey::name));
        Map<PaymentKey, PaymentTotals> movements = new TreeMap<>(Comparator
                .comparingInt(PaymentKey::type).thenComparing(PaymentKey::name));
        long sales = 0;
        long returns = 0;

        for (byte[] input : fiscalizedSigningInputs) {
            Document document = parse(input);
            Element dat = firstDirectChild(document.getDocumentElement(), "DAT");
            if (dat == null) throw invalid("Signing input does not contain DAT.");
            Element check = firstDirectChild(dat, "C");
            if (check == null) throw invalid("Fiscalized CHK input does not contain C.");
            String type = requiredAttribute(check, "T");
            if (!"0".equals(type) && !"1".equals(type)) {
                throw invalid("ZREPORT aggregation supports only sale and return CHK documents; C.T=" + type);
            }
            boolean sale = "0".equals(type);
            Element closing = firstDirectChild(check, "E");
            if (closing == null || "1".equals(closing.getAttribute("VD"))) {
                throw invalid("Voided or incomplete CHK cannot be aggregated into ZREPORT.");
            }
            rejectUnsupportedReceiptOperations(check);

            BigDecimal receiptTotal = minor(requiredLongAttribute(closing, "SM"));
            Map<String, BigDecimal> itemTurnover = readItems(check);
            BigDecimal itemTotal = itemTurnover.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (itemTotal.compareTo(receiptTotal) != 0) {
                throw invalid("CHK item total does not equal E.SM; discounts, surcharges or rounding need explicit support.");
            }

            Map<String, TaxMetadata> metadataByCode = readTaxMetadata(closing);
            if (!metadataByCode.keySet().equals(itemTurnover.keySet())) {
                throw invalid("CHK tax groups in E do not match item tax groups.");
            }
            for (Map.Entry<String, BigDecimal> entry : itemTurnover.entrySet()) {
                TaxMetadata metadata = metadataByCode.get(entry.getKey());
                TaxTotals totals = taxes.computeIfAbsent(entry.getKey(),
                        ignored -> new TaxTotals(entry.getKey(), metadata));
                totals.assertCompatible(metadata);
                if (sale) {
                    totals.receivedTurnover = totals.receivedTurnover.add(entry.getValue());
                    totals.receivedTax = totals.receivedTax.add(metadata.taxAmount());
                    totals.receivedAdditionalTax = totals.receivedAdditionalTax.add(metadata.additionalTax());
                } else {
                    totals.paidTurnover = totals.paidTurnover.add(entry.getValue());
                    totals.paidTax = totals.paidTax.add(metadata.taxAmount());
                    totals.paidAdditionalTax = totals.paidAdditionalTax.add(metadata.additionalTax());
                }
            }

            Map<PaymentKey, BigDecimal> receiptPayments = readPayments(check);
            BigDecimal paymentTotal = receiptPayments.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (paymentTotal.compareTo(receiptTotal) != 0) {
                throw invalid("CHK payment total does not equal E.SM.");
            }
            receiptPayments.forEach((key, amount) -> {
                PaymentTotals totals = payments.computeIfAbsent(key, ignored -> new PaymentTotals());
                if (sale) totals.received = totals.received.add(amount);
                else totals.paid = totals.paid.add(amount);
            });
            aggregateCashMovements(check, movements);
            if (sale) sales++; else returns++;
        }

        List<PrroXmlDocumentBuilder.TaxSummary> taxRows = taxes.values().stream()
                .map(TaxTotals::toSummary).toList();
        List<PrroXmlDocumentBuilder.PaymentTurnover> paymentRows = payments.entrySet().stream()
                .map(entry -> new PrroXmlDocumentBuilder.PaymentTurnover(
                        paymentType(entry.getKey().type()), entry.getKey().name(),
                        entry.getValue().received, entry.getValue().paid)).toList();
        List<PrroXmlDocumentBuilder.CashMovementSummary> movementRows = movements.entrySet().stream()
                .map(entry -> new PrroXmlDocumentBuilder.CashMovementSummary(
                        paymentType(entry.getKey().type()), entry.getKey().name(),
                        entry.getValue().received, entry.getValue().paid)).toList();
        return new Aggregates(taxRows, paymentRows, movementRows,
                new PrroXmlDocumentBuilder.CheckCounts(sales, returns));
    }

    private static Map<String, BigDecimal> readItems(Element check) {
        Map<String, BigDecimal> result = new TreeMap<>();
        for (Element item : directChildren(check, "P")) {
            String taxCode = requiredAttribute(item, "TX");
            result.merge(taxCode, minor(requiredLongAttribute(item, "SM")), BigDecimal::add);
        }
        if (result.isEmpty()) throw invalid("Sale/return CHK contains no P items.");
        return result;
    }

    private static Map<String, TaxMetadata> readTaxMetadata(Element closing) {
        Map<String, TaxMetadata> result = new TreeMap<>();
        List<Element> nested = directChildren(closing, "TX");
        if (!nested.isEmpty()) {
            for (Element tax : nested) {
                String code = requiredAttribute(tax, "TX");
                if (result.put(code, metadata(tax)) != null) throw invalid("Duplicate tax group in E.");
            }
        } else {
            String code = requiredAttribute(closing, "TX");
            result.put(code, metadata(closing));
        }
        return result;
    }

    private static TaxMetadata metadata(Element element) {
        return new TaxMetadata(decimalAttribute(element, "TXPR"),
                decimalAttribute(element, "TXSM"), decimalAttribute(element, "DTPR"),
                decimalAttribute(element, "DTSM"), integerAttribute(element, "TXTY"),
                integerAttribute(element, "TXAL"));
    }

    private static Map<PaymentKey, BigDecimal> readPayments(Element check) {
        Map<PaymentKey, BigDecimal> result = new TreeMap<>(Comparator
                .comparingInt(PaymentKey::type).thenComparing(PaymentKey::name));
        for (Element payment : directChildren(check, "M")) {
            int type = (int) requiredLongAttribute(payment, "T");
            String name = requiredAttribute(payment, "NM");
            validatePaymentIdentity(type, name);
            if (payment.hasAttribute("SMB") || payment.hasAttribute("SMM")
                    || payment.hasAttribute("SMP")) {
                throw invalid("Cash rounding attributes are not yet supported by ZREPORT aggregation.");
            }
            result.merge(new PaymentKey(type, name),
                    minor(requiredLongAttribute(payment, "SM")), BigDecimal::add);
        }
        if (result.isEmpty()) throw invalid("CHK contains no M payment rows.");
        return result;
    }

    private static void aggregateCashMovements(Element check, Map<PaymentKey, PaymentTotals> totals) {
        for (String tag : List.of("I", "O")) {
            for (Element movement : directChildren(check, tag)) {
                int type = (int) requiredLongAttribute(movement, "T");
                String name = requiredAttribute(movement, "NM");
                validatePaymentIdentity(type, name);
                PaymentTotals value = totals.computeIfAbsent(new PaymentKey(type, name),
                        ignored -> new PaymentTotals());
                BigDecimal amount = minor(requiredLongAttribute(movement, "SM"));
                if ("I".equals(tag)) value.received = value.received.add(amount);
                else value.paid = value.paid.add(amount);
            }
        }
    }

    private static void rejectUnsupportedReceiptOperations(Element check) {
        for (String tag : List.of("D", "S", "CP", "VD")) {
            if (!directChildren(check, tag).isEmpty()) {
                throw invalid("CHK contains unsupported " + tag + " operation; ZREPORT totals cannot be derived safely.");
            }
        }
    }

    private static void validatePaymentIdentity(int type, String name) {
        if (type < 0 || type > 2 || name.isBlank()) throw invalid("Invalid CHK payment identity.");
        if (type == 0 && !"Готівка".equals(name)) throw invalid("Cash payment must be named Готівка.");
    }

    private static PrroXmlDocumentBuilder.PaymentType paymentType(int type) {
        return switch (type) {
            case 0 -> PrroXmlDocumentBuilder.PaymentType.CASH;
            case 1 -> PrroXmlDocumentBuilder.PaymentType.OTHER;
            case 2 -> PrroXmlDocumentBuilder.PaymentType.PAYMENT_INSTRUMENT;
            default -> throw invalid("Unsupported payment type " + type);
        };
    }

    private static Document parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw invalid("Fiscalized signing_input is empty.");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(bytes)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to parse persisted fiscalized signing_input.", ex);
        }
    }

    private static Element firstDirectChild(Element parent, String name) {
        for (Element child : directChildren(parent, name)) return child;
        return null;
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) result.add(element);
        }
        return result;
    }

    private static String requiredAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) throw invalid("Missing required XML attribute " + name + ".");
        return value;
    }

    private static long requiredLongAttribute(Element element, String name) {
        try { return Long.parseLong(requiredAttribute(element, name)); }
        catch (NumberFormatException ex) { throw invalid("Invalid integer XML attribute " + name + "."); }
    }

    private static BigDecimal decimalAttribute(Element element, String name) {
        if (!element.hasAttribute(name) || element.getAttribute(name).isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(element.getAttribute(name)); }
        catch (NumberFormatException ex) { throw invalid("Invalid decimal XML attribute " + name + "."); }
    }

    private static Integer integerAttribute(Element element, String name) {
        if (!element.hasAttribute(name) || element.getAttribute(name).isBlank()) return 0;
        try { return Integer.valueOf(element.getAttribute(name)); }
        catch (NumberFormatException ex) { throw invalid("Invalid integer XML attribute " + name + "."); }
    }

    private static BigDecimal minor(long value) {
        if (value < 0) throw invalid("Negative CHK amount is not supported.");
        return BigDecimal.valueOf(value, 2).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record Aggregates(List<PrroXmlDocumentBuilder.TaxSummary> taxSummaries,
                             List<PrroXmlDocumentBuilder.PaymentTurnover> paymentTurnovers,
                             List<PrroXmlDocumentBuilder.CashMovementSummary> cashMovements,
                             PrroXmlDocumentBuilder.CheckCounts checkCounts) {}

    private record PaymentKey(int type, String name) {}
    private record TaxMetadata(BigDecimal rate, BigDecimal taxAmount, BigDecimal additionalRate,
                               BigDecimal additionalTax, Integer included, Integer algorithm) {}

    private static final class PaymentTotals {
        private BigDecimal received = BigDecimal.ZERO.setScale(2);
        private BigDecimal paid = BigDecimal.ZERO.setScale(2);
    }

    private static final class TaxTotals {
        private final String code;
        private final TaxMetadata metadata;
        private BigDecimal receivedTurnover = BigDecimal.ZERO.setScale(2);
        private BigDecimal paidTurnover = BigDecimal.ZERO.setScale(2);
        private BigDecimal receivedTax = BigDecimal.ZERO.setScale(2);
        private BigDecimal paidTax = BigDecimal.ZERO.setScale(2);
        private BigDecimal receivedAdditionalTax = BigDecimal.ZERO.setScale(2);
        private BigDecimal paidAdditionalTax = BigDecimal.ZERO.setScale(2);

        private TaxTotals(String code, TaxMetadata metadata) {
            this.code = code;
            this.metadata = metadata;
        }

        private void assertCompatible(TaxMetadata other) {
            if (!metadata.equals(other)) throw invalid("Tax parameters changed inside the shift for one tax code.");
        }

        private PrroXmlDocumentBuilder.TaxSummary toSummary() {
            return new PrroXmlDocumentBuilder.TaxSummary(code, null, zeroToNull(metadata.rate()),
                    receivedTax, paidTax, zeroToNull(metadata.additionalRate()),
                    receivedAdditionalTax, paidAdditionalTax, metadata.included(),
                    metadata.algorithm(), receivedTurnover, paidTurnover);
        }
    }

    private static BigDecimal zeroToNull(BigDecimal value) {
        return value == null || value.signum() == 0 ? null : value;
    }
}
