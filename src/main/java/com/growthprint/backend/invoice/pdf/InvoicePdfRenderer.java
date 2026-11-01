package com.growthprint.backend.invoice.pdf;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.config.CompanyProperties;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoiceCurrency;
import com.growthprint.backend.invoice.entity.InvoiceLineItem;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders an invoice as a PDF (A4, OpenPDF) from persisted data only.
 * <ul>
 *     <li>Subtotal, tax and total are printed exactly as stored on the invoice; they are never recalculated.
 *     Amount paid / balance due come from {@link InvoicePdfData}.</li>
 *     <li>Seller details and payment instructions come only from {@link CompanyProperties}; unconfigured
 *     blocks are omitted, nothing is invented.</li>
 *     <li>Multi-page safe: the line-item table repeats its header on every page, long descriptions wrap inside
 *     their cell, the totals block is kept together, and every page carries a footer with the invoice number
 *     and "Page x of y" (stamped in a second pass once the page count is known).</li>
 * </ul>
 */
@Component
public class InvoicePdfRenderer {

    private static final Color INK = new Color(0x1A, 0x1A, 0x1A);
    private static final Color MUTED = new Color(0x6B, 0x6B, 0x6B);
    private static final Color RULE = new Color(0xDD, 0xDD, 0xD8);
    private static final Color PANEL = new Color(0xF5, 0xF4, 0xEF);
    private static final Color ACCENT = new Color(0x14, 0x53, 0x3C);

    private static final Font BRAND = font(17, Font.BOLD, INK);
    private static final Font TITLE = font(24, Font.BOLD, ACCENT);
    private static final Font LABEL = font(7.5f, Font.BOLD, MUTED);
    private static final Font BODY = font(9.5f, Font.NORMAL, INK);
    private static final Font BODY_BOLD = font(9.5f, Font.BOLD, INK);
    private static final Font SMALL = font(8.5f, Font.NORMAL, MUTED);
    private static final Font TOTAL = font(11, Font.BOLD, INK);
    private static final Font BALANCE = font(12, Font.BOLD, Color.WHITE);
    private static final Font FOOTER = font(7.5f, Font.NORMAL, MUTED);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final Map<InvoiceCurrency, String> SYMBOLS = new EnumMap<>(Map.of(
            InvoiceCurrency.USD, "$",
            InvoiceCurrency.EUR, "€",
            InvoiceCurrency.GBP, "£",
            InvoiceCurrency.CAD, "CA$",
            InvoiceCurrency.AUD, "A$"));

    private static final float MARGIN_X = 50;
    private static final float MARGIN_TOP = 50;
    private static final float MARGIN_BOTTOM = 60;

    private final CompanyProperties company;

    public InvoicePdfRenderer(CompanyProperties company) {
        this.company = company;
    }

    public byte[] render(InvoicePdfData data) {
        try {
            return stampFooters(renderBody(data), data.invoice());
        } catch (DocumentException | IOException e) {
            throw new InvoicePdfGenerationException("Could not generate the invoice PDF", e);
        }
    }

    // ---------------------------------------------------------------- body

    private byte[] renderBody(InvoicePdfData data) throws DocumentException {
        Invoice invoice = data.invoice();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, MARGIN_X, MARGIN_X, MARGIN_TOP, MARGIN_BOTTOM);
        PdfWriter.getInstance(document, out);
        document.addTitle("Invoice " + invoice.getInvoiceNumber());
        if (CompanyProperties.hasText(company.getName())) {
            document.addAuthor(company.getName().trim());
        }
        document.addCreator("GrowthPrint");
        document.open();

        document.add(masthead(invoice));
        document.add(rule(14, 16));
        document.add(parties(data));
        document.add(lineItems(invoice, data.lineItems()));
        document.add(totals(data));

        if (CompanyProperties.hasText(invoice.getNotes())) {
            document.add(section("NOTES", List.of(invoice.getNotes().trim()), false));
        }
        List<String> paymentLines = company.paymentInstructionLines();
        if (!paymentLines.isEmpty()) {
            document.add(section("Payment instructions", paymentLines, true));
        }

        document.close();
        return out.toByteArray();
    }

    private PdfPTable masthead(Invoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{60, 40});
        table.setWidthPercentage(100);

        PdfPCell seller = cell();
        if (CompanyProperties.hasText(company.getName())) {
            seller.addElement(new Paragraph(company.getName().trim(), BRAND));
        }
        List<String> sellerLines = new ArrayList<>(company.addressLines());
        addIfPresent(sellerLines, company.getEmail());
        addIfPresent(sellerLines, company.getPhone());
        addIfPresent(sellerLines, company.getWebsite());
        if (CompanyProperties.hasText(company.getTaxId())) {
            sellerLines.add("Tax ID: " + company.getTaxId().trim());
        }
        if (CompanyProperties.hasText(company.getRegistrationNumber())) {
            sellerLines.add("Registration no.: " + company.getRegistrationNumber().trim());
        }
        for (String line : sellerLines) {
            Paragraph paragraph = new Paragraph(line, SMALL);
            paragraph.setLeading(11);
            seller.addElement(paragraph);
        }
        table.addCell(seller);

        PdfPCell heading = cell();
        heading.addElement(aligned(new Paragraph("INVOICE", TITLE), Element.ALIGN_RIGHT));
        Paragraph number = aligned(new Paragraph(invoice.getInvoiceNumber(), BODY_BOLD), Element.ALIGN_RIGHT);
        number.setSpacingBefore(2);
        heading.addElement(number);
        heading.addElement(statusBadge(invoice.getStatus()));
        table.addCell(heading);
        return table;
    }

    private PdfPTable statusBadge(InvoiceStatus status) throws DocumentException {
        PdfPTable badge = new PdfPTable(1);
        badge.setTotalWidth(70);
        badge.setLockedWidth(true);
        badge.setHorizontalAlignment(Element.ALIGN_RIGHT);
        badge.setSpacingBefore(6);
        PdfPCell cell = new PdfPCell(new Phrase(status.name(), font(7.5f, Font.BOLD, Color.WHITE)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(statusColor(status));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPaddingTop(3);
        cell.setPaddingBottom(5);
        badge.addCell(cell);
        return badge;
    }

    private PdfPTable parties(InvoicePdfData data) throws DocumentException {
        Invoice invoice = data.invoice();
        Client client = data.client();

        PdfPTable table = new PdfPTable(new float[]{55, 45});
        table.setWidthPercentage(100);
        table.setSpacingAfter(22);

        PdfPCell billTo = cell();
        billTo.addElement(new Paragraph("BILL TO", LABEL));
        Paragraph name = new Paragraph(invoice.getClientName(), BODY_BOLD);
        name.setSpacingBefore(3);
        billTo.addElement(name);
        List<String> clientLines = new ArrayList<>();
        if (client != null && CompanyProperties.hasText(client.getLegalName())
                && !client.getLegalName().trim().equalsIgnoreCase(invoice.getClientName().trim())) {
            clientLines.add(client.getLegalName().trim());
        }
        addIfPresent(clientLines, invoice.getClientEmail());
        if (client != null) {
            addIfPresent(clientLines, client.getPhone());
        }
        for (String line : clientLines) {
            billTo.addElement(new Paragraph(line, BODY));
        }
        table.addCell(billTo);

        PdfPTable facts = new PdfPTable(new float[]{50, 50});
        facts.setWidthPercentage(100);
        fact(facts, "Issue date", DATE.format(invoice.getIssueDate()));
        fact(facts, "Due date", DATE.format(invoice.getDueDate()));
        if (invoice.getPaidAt() != null) {
            fact(facts, "Paid on", DATE.format(LocalDate.ofInstant(invoice.getPaidAt(), ZoneOffset.UTC)));
        }
        fact(facts, "Status", invoice.getStatus().name());
        fact(facts, "Currency", invoice.getCurrency().name());
        PdfPCell factsCell = cell();
        factsCell.addElement(facts);
        table.addCell(factsCell);
        return table;
    }

    private void fact(PdfPTable facts, String label, String value) {
        PdfPCell labelCell = cell(new Phrase(label, SMALL));
        labelCell.setPaddingBottom(4);
        facts.addCell(labelCell);
        PdfPCell valueCell = cell(new Phrase(value, BODY_BOLD));
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPaddingBottom(4);
        facts.addCell(valueCell);
    }

    private PdfPTable lineItems(Invoice invoice, List<InvoiceLineItem> items) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{58, 9, 16.5f, 16.5f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitLate(false);   // let a tall row (long description) start on the current page
        table.setSplitRows(true);

        headerCell(table, "Description", Element.ALIGN_LEFT);
        headerCell(table, "Qty", Element.ALIGN_RIGHT);
        headerCell(table, "Unit price", Element.ALIGN_RIGHT);
        headerCell(table, "Amount", Element.ALIGN_RIGHT);

        InvoiceCurrency currency = invoice.getCurrency();
        for (InvoiceLineItem item : items) {
            itemCell(table, item.getDescription() == null ? "" : item.getDescription(), Element.ALIGN_LEFT, BODY);
            itemCell(table, String.valueOf(item.getQuantity()), Element.ALIGN_RIGHT, BODY);
            itemCell(table, formatMoney(item.getUnitPrice(), currency), Element.ALIGN_RIGHT, BODY);
            itemCell(table, formatMoney(item.getAmount(), currency), Element.ALIGN_RIGHT, BODY_BOLD);
        }
        return table;
    }

    private void headerCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, LABEL));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(INK);
        cell.setBorderWidth(0.8f);
        cell.setHorizontalAlignment(align);
        cell.setPaddingBottom(6);
        cell.setPaddingLeft(align == Element.ALIGN_LEFT ? 0 : 4);
        table.addCell(cell);
    }

    private void itemCell(PdfPTable table, String text, int align, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setBorderWidth(0.5f);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setPaddingTop(7);
        cell.setPaddingBottom(8);
        cell.setPaddingLeft(align == Element.ALIGN_LEFT ? 0 : 4);
        cell.setPaddingRight(align == Element.ALIGN_LEFT ? 10 : 0);
        cell.setLeading(0, 1.3f);
        table.addCell(cell);
    }

    private PdfPTable totals(InvoicePdfData data) throws DocumentException {
        Invoice invoice = data.invoice();
        InvoiceCurrency currency = invoice.getCurrency();

        PdfPTable table = new PdfPTable(new float[]{55, 45});
        table.setWidthPercentage(46);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingBefore(14);
        table.setKeepTogether(true);

        totalRow(table, "Subtotal", formatMoney(invoice.getSubtotal(), currency), BODY, false);
        totalRow(table, "Tax (" + percent(invoice.getTaxPercent()) + "%)",
                formatMoney(invoice.getTaxAmount(), currency), BODY, false);
        totalRow(table, "Total", formatMoney(invoice.getTotalAmount(), currency), TOTAL, true);
        totalRow(table, "Amount paid", formatMoney(data.amountPaid(), currency), BODY, false);

        PdfPCell balanceLabel = cell(new Phrase("Balance due", BALANCE));
        PdfPCell balanceValue = cell(new Phrase(formatMoney(data.balanceDue(), currency), BALANCE));
        for (PdfPCell balance : List.of(balanceLabel, balanceValue)) {
            balance.setBackgroundColor(ACCENT);
            balance.setPaddingTop(7);
            balance.setPaddingBottom(9);
        }
        balanceLabel.setPaddingLeft(8);
        balanceValue.setPaddingRight(8);
        balanceValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(balanceLabel);
        table.addCell(balanceValue);

        PdfPCell note = cell(new Phrase("All amounts in " + currency.name(), SMALL));
        note.setColspan(2);
        note.setHorizontalAlignment(Element.ALIGN_RIGHT);
        note.setPaddingTop(5);
        table.addCell(note);
        return table;
    }

    private void totalRow(PdfPTable table, String label, String value, Font font, boolean emphasised) {
        PdfPCell labelCell = cell(new Phrase(label, emphasised ? font : SMALL));
        PdfPCell valueCell = cell(new Phrase(value, font));
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell cell : List.of(labelCell, valueCell)) {
            cell.setPaddingTop(emphasised ? 8 : 4);
            cell.setPaddingBottom(emphasised ? 8 : 4);
            if (emphasised) {
                cell.setBorder(Rectangle.TOP);
                cell.setBorderColor(INK);
                cell.setBorderWidth(0.8f);
            }
        }
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private PdfPTable section(String title, List<String> lines, boolean panel) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(24);
        table.setKeepTogether(true);
        PdfPCell cell = cell();
        if (panel) {
            cell.setBackgroundColor(PANEL);
            cell.setPadding(12);
        }
        cell.addElement(new Paragraph(title.toUpperCase(Locale.ROOT), LABEL));
        for (int i = 0; i < lines.size(); i++) {
            Paragraph paragraph = new Paragraph(lines.get(i), BODY);
            paragraph.setLeading(13);
            if (i == 0) {
                paragraph.setSpacingBefore(4);
            }
            cell.addElement(paragraph);
        }
        table.addCell(cell);
        return table;
    }

    private static Paragraph rule(float before, float after) {
        Paragraph paragraph = new Paragraph(new Chunk(new org.openpdf.text.pdf.draw.LineSeparator(0.6f, 100, RULE,
                Element.ALIGN_CENTER, 0)));
        paragraph.setSpacingBefore(before);
        paragraph.setSpacingAfter(after);
        return paragraph;
    }

    // ---------------------------------------------------------------- footer (second pass)

    private byte[] stampFooters(byte[] body, Invoice invoice) throws IOException, DocumentException {
        PdfReader reader = new PdfReader(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, out);
        int pages = reader.getNumberOfPages();
        String left = (CompanyProperties.hasText(company.getName()) ? company.getName().trim() + "  ·  " : "")
                + "Invoice " + invoice.getInvoiceNumber();
        for (int page = 1; page <= pages; page++) {
            Rectangle size = reader.getPageSize(page);
            PdfContentByte canvas = stamper.getOverContent(page);
            float y = 34;
            canvas.saveState();
            canvas.setColorStroke(RULE);
            canvas.setLineWidth(0.5f);
            canvas.moveTo(MARGIN_X, y + 12);
            canvas.lineTo(size.getWidth() - MARGIN_X, y + 12);
            canvas.stroke();
            canvas.restoreState();
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(left, FOOTER), MARGIN_X, y, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                    new Phrase("Page " + page + " of " + pages, FOOTER), size.getWidth() - MARGIN_X, y, 0);
        }
        stamper.close();
        reader.close();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- formatting helpers

    /** Money with the currency's symbol, grouping and exactly two decimals, e.g. {@code €1,234.50}. */
    static String formatMoney(BigDecimal amount, InvoiceCurrency currency) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        String digits = format.format(value.abs());
        return (value.signum() < 0 ? "-" : "") + SYMBOLS.get(currency) + digits;
    }

    private static String percent(BigDecimal percent) {
        if (percent == null) {
            return "0";
        }
        return percent.stripTrailingZeros().toPlainString();
    }

    private static Color statusColor(InvoiceStatus status) {
        return switch (status) {
            case PAID -> ACCENT;
            case OVERDUE -> new Color(0xB4, 0x23, 0x18);
            case ISSUED -> new Color(0x1F, 0x4E, 0x8C);
            case DRAFT, VOID -> MUTED;
        };
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (CompanyProperties.hasText(value)) {
            lines.add(value.trim());
        }
    }

    private static Paragraph aligned(Paragraph paragraph, int alignment) {
        paragraph.setAlignment(alignment);
        return paragraph;
    }

    private static PdfPCell cell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private static PdfPCell cell(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private static Font font(float size, int style, Color color) {
        return new Font(Font.HELVETICA, size, style, color);
    }
}
