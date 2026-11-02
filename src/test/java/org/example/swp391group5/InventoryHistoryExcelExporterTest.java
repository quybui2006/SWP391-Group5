package org.example.swp391group5;

import dto.shop.InventoryBatchHistoryRow;
import org.junit.jupiter.api.Test;
import service.shop.InventoryHistoryExcelExporter;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryHistoryExcelExporterTest {
    @Test
    void createsAnExcelWorkbookWithInventoryTransactions() throws Exception {
        InventoryBatchHistoryRow row = new InventoryBatchHistoryRow(
                1L, LocalDateTime.of(2026, 11, 2, 8, 30, 0), "RECEIVE", 10, 0, 10, 0,
                "Nhập <đợt> & kiểm tra", "Táo & Lê", "Loại 1 kg", "SKU-001", "kg", "LOT-00000001", null);

        byte[] file = new InventoryHistoryExcelExporter().export(List.of(row));

        assertArrayEquals(new byte[]{0x50, 0x4b, 0x03, 0x04}, new byte[]{file[0], file[1], file[2], file[3]});
        String sheet = findSheet(file);
        assertTrue(sheet.contains("Thời điểm"));
        assertTrue(sheet.contains("LOT-00000001"));
        assertTrue(sheet.contains("+10 kg"));
        assertTrue(sheet.contains("Táo &amp; Lê"));
        assertTrue(sheet.contains("Nhập &lt;đợt&gt; &amp; kiểm tra"));
    }

    private String findSheet(byte[] file) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("Excel worksheet was not created");
    }
}
