package service.shop;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ShopOrderExcelExporter {
    private static final List<String> HEADERS = List.of(
            "Mã đơn", "Ngày tạo", "Người nhận", "Số điện thoại", "Địa chỉ", "Sản phẩm",
            "Tiền hàng", "Phí giao hàng", "Tổng tiền", "Thanh toán", "Trạng thái", "Lý do hủy/từ chối");
    private static final List<String> FIELDS = List.of(
            "order_code", "created_at", "recipient_name", "recipient_phone", "delivery_address", "order_items",
            "subtotal", "shipping_fee", "total_amount", "payment_method", "status", "cancellation_reason");

    public byte[] export(List<Map<String, Object>> rows) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            add(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            add(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            add(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Đơn hàng\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            add(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            appendRow(sheet, 1, HEADERS);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                appendRow(sheet, i + 2, FIELDS.stream().map(field -> row.get(field) == null ? "" : String.valueOf(row.get(field))).toList());
            }
            sheet.append("</sheetData></worksheet>");
            add(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo file Excel đơn hàng.", e);
        }
    }

    private void appendRow(StringBuilder xml, int rowNumber, List<String> values) {
        xml.append("<row r=\"").append(rowNumber).append("\">");
        for (int i = 0; i < values.size(); i++) {
            xml.append("<c r=\"").append(columnName(i + 1)).append(rowNumber).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(values.get(i))).append("</t></is></c>");
        }
        xml.append("</row>");
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
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private void add(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
