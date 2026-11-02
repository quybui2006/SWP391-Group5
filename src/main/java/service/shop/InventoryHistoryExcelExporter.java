package service.shop;

import dto.shop.InventoryBatchHistoryRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class InventoryHistoryExcelExporter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final List<String> HEADERS = List.of(
            "Thời điểm", "Sản phẩm", "Biến thể", "Mã SKU", "Mã lô", "Giao dịch",
            "Thay đổi tồn", "Thay đổi đang giữ", "Tồn sau giao dịch", "Đang giữ sau giao dịch", "Mã đơn", "Ghi chú");

    public byte[] export(List<InventoryBatchHistoryRow> rows) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            add(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            add(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            add(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Lịch sử tồn kho\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            add(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");

            // Inline text cells keep user-entered notes from being interpreted as spreadsheet formulas.
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            appendRow(sheet, 1, HEADERS);
            for (int index = 0; index < rows.size(); index++) {
                InventoryBatchHistoryRow row = rows.get(index);
                appendRow(sheet, index + 2, List.of(
                        row.createdAt() == null ? "" : row.createdAt().format(DATE_FORMAT),
                        text(row.productName()), text(row.variantName()), text(row.sku()), text(row.batchCode()), row.typeLabel(),
                        quantity(row.onHandDelta(), row.unitName()), quantity(row.reservedDelta(), row.unitName()),
                        quantity(row.onHandAfter(), row.unitName()), quantity(row.reservedAfter(), row.unitName()),
                        text(row.orderCode()), text(row.reason())));
            }
            sheet.append("</sheetData></worksheet>");
            add(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo file Excel lịch sử tồn kho.", exception);
        }
    }

    private void appendRow(StringBuilder xml, int rowNumber, List<String> values) {
        xml.append("<row r=\"").append(rowNumber).append("\">");
        for (int index = 0; index < values.size(); index++) {
            xml.append("<c r=\"").append(columnName(index + 1)).append(rowNumber)
                    .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(values.get(index))).append("</t></is></c>");
        }
        xml.append("</row>");
    }

    private String quantity(int value, String unit) {
        return (value > 0 ? "+" : "") + value + (unit == null || unit.isBlank() ? "" : " " + unit);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String columnName(int column) {
        StringBuilder name = new StringBuilder();
        while (column > 0) {
            int remainder = (column - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            column = (column - 1) / 26;
        }
        return name.toString();
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint != 0x9 && codePoint != 0xA && codePoint != 0xD
                    && (codePoint < 0x20 || codePoint > 0xD7FF && codePoint < 0xE000
                    || codePoint > 0xFFFD && codePoint < 0x10000 || codePoint > 0x10FFFF)) return;
            switch (codePoint) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.appendCodePoint(codePoint);
            }
        });
        return escaped.toString();
    }

    private void add(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
