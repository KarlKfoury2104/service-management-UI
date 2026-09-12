package com.mouchy.app.pdf;

import com.mouchy.app.models.ClientUser;
import com.mouchy.app.models.ServiceRequest;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles PDF report generation using the OpenPDF library.
 */
public class PDFGenerator {

    /**
     * Generates a beautifully styled PDF report containing client profile details and booking history.
     *
     * @param client      The client user.
     * @param requests    The list of bookings/requests made by this client.
     * @param destination The output file path.
     * @throws DocumentException if PDF construction fails.
     * @throws IOException       if file writing fails.
     */
    public static void generateClientReport(ClientUser client, List<ServiceRequest> requests, String destination) 
            throws DocumentException, IOException {
        
        Document document = new Document(PageSize.A4, 36, 36, 54, 54);
        PdfWriter.getInstance(document, new FileOutputStream(destination));
        
        document.open();

        // Fonts
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(108, 99, 255)); // Accent Purple
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(42, 42, 60));
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font tableBodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC, Color.GRAY);

        // Header / Cover Info
        Paragraph title = new Paragraph("Mouchy Service Portal", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph("Client Information & Booking History Report", 
                FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY));
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);

        // Divider Line
        Paragraph divider = new Paragraph("----------------------------------------------------------------------------------------------------------------------------------", 
                FontFactory.getFont(FontFactory.HELVETICA, 8, Color.LIGHT_GRAY));
        divider.setSpacingAfter(15);
        document.add(divider);

        // Client Profile Section
        Paragraph profileTitle = new Paragraph("Client Profile Details", sectionFont);
        profileTitle.setSpacingAfter(10);
        document.add(profileTitle);

        PdfPTable profileTable = new PdfPTable(2);
        profileTable.setWidthPercentage(100);
        profileTable.setWidths(new float[]{30f, 70f});
        
        addProfileRow(profileTable, "Full Name:", client.getFullName(), labelFont, valueFont);
        addProfileRow(profileTable, "Username:", client.getUsername(), labelFont, valueFont);
        addProfileRow(profileTable, "Email Address:", client.getEmail(), labelFont, valueFont);
        addProfileRow(profileTable, "Phone Number:", client.getPhoneNumber(), labelFont, valueFont);
        addProfileRow(profileTable, "Role Account Type:", client.getRole(), labelFont, valueFont);
        
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        addProfileRow(profileTable, "Report Generated On:", now.format(formatter), labelFont, valueFont);

        profileTable.setSpacingAfter(25);
        document.add(profileTable);

        // Booking History Section
        Paragraph historyTitle = new Paragraph("Booking History Details", sectionFont);
        historyTitle.setSpacingAfter(10);
        document.add(historyTitle);

        if (requests.isEmpty()) {
            Paragraph noBookings = new Paragraph("No service requests found for this client.", valueFont);
            noBookings.setSpacingAfter(20);
            document.add(noBookings);
        } else {
            // Table for bookings
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{8f, 22f, 25f, 15f, 15f, 15f}); // Col width ratios

            // Table Headers
            String[] headers = {"ID", "Service Name", "Request Title", "Price", "Date Booked", "Status"};
            for (String headerText : headers) {
                PdfPCell headerCell = new PdfPCell(new Paragraph(headerText, tableHeaderFont));
                headerCell.setBackgroundColor(new Color(42, 42, 60)); // Dark Gray/Navy
                headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                headerCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                headerCell.setPadding(6);
                table.addCell(headerCell);
            }

            double completedInvestment = 0.0;
            int completedCount = 0;

            // Populate table data
            for (ServiceRequest req : requests) {
                table.addCell(createCenterCell(String.valueOf(req.getId()), tableBodyFont));
                table.addCell(createLeftCell(req.getServiceName(), tableBodyFont));
                table.addCell(createLeftCell(req.getTitle(), tableBodyFont));
                table.addCell(createRightCell(String.format("$%.2f", req.getPrice()), tableBodyFont));
                
                // Truncate timestamp to date (YYYY-MM-DD)
                String createdAt = req.getCreatedAt();
                String dateBooked = (createdAt != null && createdAt.length() >= 10)
                        ? createdAt.substring(0, 10)
                        : (createdAt == null ? "-" : createdAt);
                table.addCell(createCenterCell(dateBooked, tableBodyFont));
                
                // Color status cell
                PdfPCell statusCell = new PdfPCell(new Paragraph(req.getStatus(), tableBodyFont));
                statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                statusCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                statusCell.setPadding(4);
                if ("COMPLETED".equalsIgnoreCase(req.getStatus())) {
                    statusCell.setBackgroundColor(new Color(230, 245, 230)); // light green
                    completedCount++;
                    completedInvestment += req.getPrice();
                } else if ("PENDING".equalsIgnoreCase(req.getStatus())) {
                    statusCell.setBackgroundColor(new Color(255, 245, 230)); // light orange
                } else if ("REJECTED".equalsIgnoreCase(req.getStatus())) {
                    statusCell.setBackgroundColor(new Color(255, 230, 230)); // light red
                } else if ("IN_PROGRESS".equalsIgnoreCase(req.getStatus())) {
                    statusCell.setBackgroundColor(new Color(230, 240, 255)); // light blue
                }
                table.addCell(statusCell);

            }

            table.setSpacingAfter(20);
            document.add(table);

            // Statistics Summary Card
            PdfPTable statsTable = new PdfPTable(3);
            statsTable.setWidthPercentage(100);
            statsTable.setWidths(new float[]{33f, 33f, 34f});

            addStatsCell(statsTable, "Total Bookings", String.valueOf(requests.size()), labelFont, valueFont);
            addStatsCell(statsTable, "Completed Jobs", String.valueOf(completedCount), labelFont, valueFont);
            addStatsCell(statsTable, "Completed Investment", String.format("$%.2f", completedInvestment), labelFont, valueFont);

            statsTable.setSpacingAfter(30);
            document.add(statsTable);
        }

        // Footer note
        Paragraph footer = new Paragraph("Thank you for choosing Mouchy Creative Agency. If you have any questions about this report, please contact support@mouchy.com.", footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
    }

    private static void addProfileRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cellLabel = new PdfPCell(new Paragraph(label, labelFont));
        cellLabel.setBorder(Rectangle.NO_BORDER);
        cellLabel.setPadding(4);
        table.addCell(cellLabel);

        PdfPCell cellValue = new PdfPCell(new Paragraph(value, valueFont));
        cellValue.setBorder(Rectangle.NO_BORDER);
        cellValue.setPadding(4);
        table.addCell(cellValue);
    }

    private static void addStatsCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(245, 245, 250)); // Very light gray-blue
        cell.setPadding(10);
        cell.setBorderWidth(1);
        cell.setBorderColor(new Color(220, 220, 230));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pLabel = new Paragraph(label.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.GRAY));
        pLabel.setAlignment(Element.ALIGN_CENTER);
        pLabel.setSpacingAfter(5);
        cell.addElement(pLabel);

        Paragraph pVal = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(108, 99, 255)));
        pVal.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pVal);

        table.addCell(cell);
    }

    private static PdfPCell createLeftCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }

    private static PdfPCell createCenterCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }

    private static PdfPCell createRightCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }
}
