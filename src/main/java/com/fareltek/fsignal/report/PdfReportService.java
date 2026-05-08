package com.fareltek.fsignal.report;

import com.fareltek.fsignal.db.SafetyEvent;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;

@Service
public class PdfReportService {

    @Value("${fsignal.server.ip:SERVER}")
    private String serverIp;

    private static final Color COL_DARK_BLUE  = new Color(15, 43, 84);
    private static final Color COL_MID_BLUE   = new Color(26, 82, 158);
    private static final Color COL_LIGHT_BLUE = new Color(235, 242, 250);
    private static final Color COL_TEXT       = new Color(44, 62, 80);
    private static final Color COL_MUTED      = new Color(100, 116, 139);
    private static final Color COL_RED        = new Color(220, 38, 38);
    private static final Color COL_ORANGE     = new Color(234, 88, 12);
    private static final Color COL_YELLOW_BG  = new Color(254, 249, 195);
    private static final Color COL_RED_BG     = new Color(254, 226, 226);
    private static final Color COL_ORANGE_BG  = new Color(255, 237, 213);

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ENCODING = "Cp1254"; // Windows Turkish — tüm Türkçe karakterleri destekler

    public byte[] generate(List<SafetyEvent> events, String from, String to,
                           String severity, String sourceAddr,
                           String messageType, Boolean acknowledged) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 30, 30, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            writer.setPageEvent(new PageFooter(writer));
            doc.open();

            addCoverSection(doc, writer, from, to, severity, sourceAddr, messageType, acknowledged, events);
            addStatisticsSection(doc, events);
            doc.newPage();
            addEventTable(doc, events);
            doc.newPage();
            addComplianceSection(doc);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF üretim hatası: " + e.getMessage(), e);
        }
    }

    // ── Cover ────────────────────────────────────────────────────────────────

    private void addCoverSection(Document doc, PdfWriter writer,
                                  String from, String to, String severity, String sourceAddr,
                                  String messageType, Boolean acknowledged,
                                  List<SafetyEvent> events) throws Exception {

        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD,   ENCODING, BaseFont.NOT_EMBEDDED);
        BaseFont bfNorm = BaseFont.createFont(BaseFont.HELVETICA,        ENCODING, BaseFont.NOT_EMBEDDED);

        // Header table (full width)
        PdfPTable hdr = new PdfPTable(2);
        hdr.setWidthPercentage(100);
        hdr.setWidths(new float[]{3f, 1.1f});
        hdr.setSpacingAfter(12);

        // Left cell
        PdfPCell left = new PdfPCell();
        left.setBackgroundColor(COL_DARK_BLUE);
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(18);

        left.addElement(new Paragraph("FSIGNAL",
                new Font(bfBold, 22, Font.BOLD, Color.WHITE)));
        left.addElement(new Paragraph("Tramway Signalization System",
                new Font(bfNorm, 9, Font.NORMAL, new Color(147, 197, 253))));
        Paragraph title = new Paragraph("GÜVENLIK OLAYI RAPORU",
                new Font(bfBold, 15, Font.BOLD, Color.WHITE));
        title.setSpacingBefore(6);
        left.addElement(title);
        left.addElement(new Paragraph("SAFETY EVENT REPORT",
                new Font(bfNorm, 8, Font.NORMAL, new Color(148, 163, 184))));
        hdr.addCell(left);

        // Right badge cell
        PdfPCell right = new PdfPCell();
        right.setBackgroundColor(COL_MID_BLUE);
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(14);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
        right.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph badge1 = new Paragraph("EN50129:2018",
                new Font(bfBold, 11, Font.BOLD, Color.WHITE));
        badge1.setAlignment(Element.ALIGN_CENTER);
        right.addElement(badge1);

        Paragraph badge2 = new Paragraph("SIL 2",
                new Font(bfBold, 18, Font.BOLD, Color.WHITE));
        badge2.setAlignment(Element.ALIGN_CENTER);
        right.addElement(badge2);

        Paragraph badge3 = new Paragraph("Safety Integrity Level",
                new Font(bfNorm, 7, Font.NORMAL, new Color(147, 197, 253)));
        badge3.setAlignment(Element.ALIGN_CENTER);
        right.addElement(badge3);
        hdr.addCell(right);

        doc.add(hdr);

        // Metadata box
        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.setWidths(new float[]{1f, 2f});
        meta.setSpacingAfter(12);

        addMetaHeader(meta, "RAPOR BİLGİLERİ / REPORT INFORMATION", bfBold);

        String now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        addMetaRow(meta, "Oluşturma Tarihi / Generated:", now + " UTC", bfBold, bfNorm);
        addMetaRow(meta, "Rapor Dönemi / Period:",
                (from != null ? from : "Tümü") + " — " + (to != null ? to : "Tümü"), bfBold, bfNorm);
        addMetaRow(meta, "Kaynak / Source:", sourceAddr != null ? sourceAddr : "Tümü / All", bfBold, bfNorm);
        addMetaRow(meta, "Ciddiyet / Severity:", severity != null ? severity : "Tümü / All", bfBold, bfNorm);
        addMetaRow(meta, "Mesaj Tipi / Msg Type:", messageType != null ? messageType : "Tümü / All", bfBold, bfNorm);
        addMetaRow(meta, "Onay Durumu / Ack Status:",
                acknowledged == null ? "Tümü / All" : (acknowledged ? "Onaylı / Acked" : "Bekliyor / Pending"), bfBold, bfNorm);
        addMetaRow(meta, "Toplam Kayıt / Total Records:", String.valueOf(events.size()), bfBold, bfNorm);
        doc.add(meta);
    }

    private void addMetaHeader(PdfPTable table, String text, BaseFont bf) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(bf, 8, Font.BOLD, COL_MID_BLUE)));
        cell.setColspan(2);
        cell.setBackgroundColor(COL_LIGHT_BLUE);
        cell.setBorderColor(COL_MID_BLUE);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addMetaRow(PdfPTable table, String key, String val, BaseFont bfBold, BaseFont bfNorm) {
        PdfPCell kCell = new PdfPCell(new Phrase(key, new Font(bfBold, 8, Font.BOLD, COL_TEXT)));
        kCell.setBorderColor(new Color(203, 213, 225));
        kCell.setBackgroundColor(new Color(248, 250, 252));
        kCell.setPadding(5);
        table.addCell(kCell);

        PdfPCell vCell = new PdfPCell(new Phrase(val, new Font(bfNorm, 8, Font.NORMAL, COL_TEXT)));
        vCell.setBorderColor(new Color(203, 213, 225));
        vCell.setPadding(5);
        table.addCell(vCell);
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    private void addStatisticsSection(Document doc, List<SafetyEvent> events) throws Exception {
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, ENCODING, BaseFont.NOT_EMBEDDED);
        BaseFont bfNorm = BaseFont.createFont(BaseFont.HELVETICA,      ENCODING, BaseFont.NOT_EMBEDDED);

        Paragraph heading = new Paragraph("ÖZET İSTATİSTİKLER / SUMMARY STATISTICS",
                new Font(bfBold, 10, Font.BOLD, COL_DARK_BLUE));
        heading.setSpacingAfter(4);
        doc.add(heading);

        long info     = events.stream().filter(e -> "INFO".equalsIgnoreCase(e.getSeverity())).count();
        long warning  = events.stream().filter(e -> "WARNING".equalsIgnoreCase(e.getSeverity())).count();
        long alarm    = events.stream().filter(e -> "ALARM".equalsIgnoreCase(e.getSeverity())).count();
        long critical = events.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity())).count();
        long unacked  = events.stream().filter(e -> !Boolean.TRUE.equals(e.getAcknowledged())).count();
        int  total    = events.size();

        PdfPTable stats = new PdfPTable(5);
        stats.setWidthPercentage(100);
        stats.setSpacingAfter(14);

        addStatCell(stats, "INFO",     String.valueOf(info),     pct(info, total),    new Color(219, 234, 254), COL_MID_BLUE, bfBold, bfNorm);
        addStatCell(stats, "WARNING",  String.valueOf(warning),  pct(warning, total), COL_YELLOW_BG,            new Color(161, 98, 7),  bfBold, bfNorm);
        addStatCell(stats, "ALARM",    String.valueOf(alarm),    pct(alarm, total),   COL_RED_BG,               COL_RED,                bfBold, bfNorm);
        addStatCell(stats, "CRITICAL", String.valueOf(critical), pct(critical, total),new Color(255, 209, 209), new Color(153, 27, 27), bfBold, bfNorm);
        addStatCell(stats, "ONAYSIZ",  String.valueOf(unacked),  pct(unacked, total), COL_ORANGE_BG,            COL_ORANGE,             bfBold, bfNorm);
        doc.add(stats);
    }

    private void addStatCell(PdfPTable table, String label, String value, String pct,
                              Color bg, Color fg, BaseFont bfBold, BaseFont bfNorm) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(new Color(203, 213, 225));
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph lbl = new Paragraph(label, new Font(bfBold, 7, Font.BOLD, fg));
        lbl.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(lbl);

        Paragraph val = new Paragraph(value, new Font(bfBold, 18, Font.BOLD, fg));
        val.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(val);

        Paragraph p = new Paragraph(pct, new Font(bfNorm, 7, Font.NORMAL, COL_MUTED));
        p.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);

        table.addCell(cell);
    }

    private String pct(long n, int total) {
        if (total == 0) return "0.0%";
        return String.format("%.1f%%", n * 100.0 / total);
    }

    // ── Event Table ───────────────────────────────────────────────────────────

    private void addEventTable(Document doc, List<SafetyEvent> events) throws Exception {
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, ENCODING, BaseFont.NOT_EMBEDDED);
        BaseFont bfNorm = BaseFont.createFont(BaseFont.HELVETICA,      ENCODING, BaseFont.NOT_EMBEDDED);

        Paragraph heading = new Paragraph("OLAY KAYITLARI / EVENT LOG",
                new Font(bfBold, 10, Font.BOLD, COL_DARK_BLUE));
        heading.setSpacingAfter(4);
        doc.add(heading);

        // Zaman, BolgeAdi, BolgeIP, Ciddiyet, Mesaj Tipi, Cihaz Tipi, CihazID, Kod, Aciklama, Onay
        PdfPTable table = new PdfPTable(10);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.4f, 1.6f, 2.0f, 1.1f, 1.4f, 1.2f, 0.7f, 0.9f, 3.8f, 1.9f});
        table.setHeaderRows(1);

        String[] headers = {"Zaman", "Bolge Adi", "Bolge IP", "Ciddiyet", "Mesaj Tipi",
                            "Kaynak Tipi", "Kaynak ID", "Data", "Aciklama", "Onay"};
        for (String h : headers) {
            PdfPCell hCell = new PdfPCell(new Phrase(h, new Font(bfBold, 6, Font.BOLD, Color.WHITE)));
            hCell.setBackgroundColor(COL_DARK_BLUE);
            hCell.setPadding(4);
            hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(hCell);
        }

        for (int i = 0; i < events.size(); i++) {
            SafetyEvent e = events.get(i);
            Color rowBg = (i % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);

            String sev = e.getSeverity() != null ? e.getSeverity() : "INFO";
            if ("CRITICAL".equals(sev))    rowBg = COL_RED_BG;
            else if ("ALARM".equals(sev))   rowBg = new Color(255, 240, 240);
            else if ("WARNING".equals(sev)) rowBg = COL_YELLOW_BG;

            String dt      = e.getEventTime()   != null ? e.getEventTime().format(DT_FMT)   : "—";
            boolean isSys  = "SYSTEM".equals(e.getMessageType());
            String bolgAdi = isSys ? "SERVER" : "—";
            String bolgIp  = isSys ? serverIp : (e.getSourceAddr() != null ? e.getSourceAddr() : "—");
            String desc    = e.getDescription()  != null
                    ? (e.getDescription().length() > 50 ? e.getDescription().substring(0, 50) + "…" : e.getDescription())
                    : "—";
            String devType = e.getDeviceType()   != null ? e.getDeviceType()                 : "—";
            String devId   = e.getDeviceId()     != null ? String.valueOf(e.getDeviceId())   : "—";
            String code    = e.getEventCode()    != null ? "0x" + Integer.toHexString(e.getEventCode()).toUpperCase() : "—";
            String ack;
            if (Boolean.TRUE.equals(e.getAcknowledged())) {
                String by   = e.getAcknowledgedBy()   != null ? e.getAcknowledgedBy()             : "—";
                String when = e.getAcknowledgedTime() != null ? e.getAcknowledgedTime().format(DT_FMT) : "";
                ack = "✓ " + by + (when.isEmpty() ? "" : "\n" + when);
            } else {
                ack = "Bekliyor";
            }

            addTableCell(table, dt,      rowBg, bfNorm, 6, Element.ALIGN_LEFT);
            addTableCell(table, bolgAdi, rowBg, bfNorm, 6, Element.ALIGN_LEFT);
            addTableCell(table, bolgIp,  rowBg, bfNorm, 6, Element.ALIGN_LEFT);
            addTableCell(table, sev,     rowBg, bfBold, 6, Element.ALIGN_CENTER);
            addTableCell(table, e.getMessageType() != null ? e.getMessageType() : "—", rowBg, bfNorm, 6, Element.ALIGN_CENTER);
            addTableCell(table, devType, rowBg, bfNorm, 6, Element.ALIGN_CENTER);
            addTableCell(table, devId,   rowBg, bfNorm, 6, Element.ALIGN_RIGHT);
            addTableCell(table, code,    rowBg, bfNorm, 6, Element.ALIGN_CENTER);
            addTableCell(table, desc,    rowBg, bfNorm, 6, Element.ALIGN_LEFT);
            addTableCell(table, ack,     rowBg, bfNorm, 6, Element.ALIGN_CENTER);
        }

        doc.add(table);
    }

    private void addTableCell(PdfPTable table, String text, Color bg,
                               BaseFont bf, int size, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(bf, size, Font.NORMAL, COL_TEXT)));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setPadding(3);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    // ── Compliance ────────────────────────────────────────────────────────────

    private void addComplianceSection(Document doc) throws Exception {
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, ENCODING, BaseFont.NOT_EMBEDDED);
        BaseFont bfNorm = BaseFont.createFont(BaseFont.HELVETICA,      ENCODING, BaseFont.NOT_EMBEDDED);

        Paragraph heading = new Paragraph("UYGUNLUK BEYANI / COMPLIANCE STATEMENT",
                new Font(bfBold, 12, Font.BOLD, COL_DARK_BLUE));
        heading.setSpacingAfter(10);
        doc.add(heading);

        String[] lines = {
            "Bu rapor, EN50129:2018 Demiryolu Uygulamalari - Iletisim, Sinyalizasyon ve Isleme",
            "Sistemleri standardina uygun olarak FSIGNAL Tramvay Sinyalizasyon Sistemi tarafindan",
            "otomatik olarak üretilmistir. Sistem SIL2 (Safety Integrity Level 2) güvenlik",
            "bütünlügü seviyesinde çalisacak sekilde tasarlanmistir.",
            "",
            "This report has been automatically generated by the FSIGNAL Tramway Signalization",
            "System in compliance with EN50129:2018 Railway Applications standard. The system is",
            "designed to operate at SIL2 (Safety Integrity Level 2) safety integrity level.",
        };
        for (String line : lines) {
            if (line.isEmpty()) {
                doc.add(new Paragraph(" "));
            } else {
                doc.add(new Paragraph(line, new Font(bfNorm, 9, Font.NORMAL, COL_TEXT)));
            }
        }

        doc.add(new Paragraph(" "));

        // Standards box
        PdfPTable stdBox = new PdfPTable(1);
        stdBox.setWidthPercentage(100);
        stdBox.setSpacingAfter(24);

        PdfPCell boxCell = new PdfPCell();
        boxCell.setBackgroundColor(COL_LIGHT_BLUE);
        boxCell.setBorderColor(COL_MID_BLUE);
        boxCell.setPadding(10);

        boxCell.addElement(new Paragraph("Ilgili Standartlar / Applicable Standards:",
                new Font(bfBold, 9, Font.BOLD, COL_MID_BLUE)));
        String[] standards = {
            "  EN50129:2018  — Railway applications: Safety related electronic systems",
            "  EN50126:2017  — Railway applications: Reliability, Availability, Maintainability, Safety",
            "  EN50159:2010  — Railway applications: Safety-related communication in railway systems",
            "  IEC62280:2014 — Railway applications: Communication security",
        };
        for (String std : standards) {
            Paragraph p = new Paragraph(std, new Font(bfNorm, 8, Font.NORMAL, COL_TEXT));
            p.setSpacingBefore(3);
            boxCell.addElement(p);
        }
        stdBox.addCell(boxCell);
        doc.add(stdBox);

        // Important note
        PdfPTable noteBox = new PdfPTable(1);
        noteBox.setWidthPercentage(100);
        noteBox.setSpacingAfter(24);

        PdfPCell noteCell = new PdfPCell();
        noteCell.setBackgroundColor(COL_YELLOW_BG);
        noteCell.setBorderColor(COL_ORANGE);
        noteCell.setPadding(10);
        noteCell.addElement(new Paragraph("ÖNEMLI NOT / IMPORTANT NOTE",
                new Font(bfBold, 8, Font.BOLD, new Color(146, 64, 14))));
        noteCell.addElement(new Paragraph(
                "Bu rapordaki veriler yetkili personel tarafindan gözden geçirilmeli ve onaylanmalidir.\n" +
                "Data in this report must be reviewed and approved by authorized personnel only.",
                new Font(bfNorm, 8, Font.NORMAL, COL_TEXT)));
        noteBox.addCell(noteCell);
        doc.add(noteBox);

        // Signature block
        Paragraph sigHdr = new Paragraph("IMZA BLOGU / SIGNATURE BLOCK",
                new Font(bfBold, 10, Font.BOLD, COL_DARK_BLUE));
        sigHdr.setSpacingAfter(8);
        doc.add(sigHdr);

        PdfPTable sigTable = new PdfPTable(3);
        sigTable.setWidthPercentage(100);
        sigTable.setWidths(new float[]{1f, 1f, 1f});

        String[] roles = {
            "Hazırlayan / Prepared by",
            "Kontrol Eden / Reviewed by",
            "Onaylayan / Authorized by"
        };
        for (String role : roles) {
            PdfPCell sig = new PdfPCell();
            sig.setBackgroundColor(new Color(248, 250, 252));
            sig.setBorderColor(new Color(203, 213, 225));
            sig.setPadding(10);
            sig.setFixedHeight(90);

            sig.addElement(new Paragraph(role, new Font(bfBold, 7, Font.BOLD, COL_MID_BLUE)));
            sig.addElement(new Paragraph("\n", new Font(bfNorm, 8)));
            sig.addElement(new Paragraph("Ad Soyad / Name:  ___________________",
                    new Font(bfNorm, 8, Font.NORMAL, COL_MUTED)));
            sig.addElement(new Paragraph("Imza / Signature: ___________________",
                    new Font(bfNorm, 8, Font.NORMAL, COL_MUTED)));
            sig.addElement(new Paragraph("Tarih / Date:     ___________________",
                    new Font(bfNorm, 8, Font.NORMAL, COL_MUTED)));
            sigTable.addCell(sig);
        }
        doc.add(sigTable);
    }

    // ── Page footer event ─────────────────────────────────────────────────────

    private static class PageFooter extends PdfPageEventHelper {
        private final PdfWriter writer;

        PageFooter(PdfWriter writer) { this.writer = writer; }

        @Override
        public void onEndPage(PdfWriter w, Document document) {
            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, ENCODING, BaseFont.NOT_EMBEDDED);
                PdfContentByte cb = writer.getDirectContent();
                float bottom = document.bottom() - 20;
                float left   = document.left();
                float right  = document.right();

                cb.setColorStroke(new Color(203, 213, 225));
                cb.setLineWidth(0.3f);
                cb.moveTo(left, bottom + 8);
                cb.lineTo(right, bottom + 8);
                cb.stroke();

                cb.beginText();
                cb.setFontAndSize(bf, 7);
                cb.setColorFill(COL_MUTED);
                cb.showTextAligned(Element.ALIGN_LEFT,
                        "FSIGNAL Tramway Signalization | EN50129:2018 / SIL2 | GIZLI / CONFIDENTIAL",
                        left, bottom, 0);
                cb.showTextAligned(Element.ALIGN_RIGHT,
                        "Sayfa " + writer.getPageNumber(),
                        right, bottom, 0);
                cb.endText();
            } catch (Exception ignored) {}
        }
    }
}
