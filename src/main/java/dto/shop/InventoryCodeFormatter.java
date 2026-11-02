package dto.shop;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chuẩn hóa phần số đệm của mã lô chỉ khi hiển thị trong các màn Shop Owner. */
public final class InventoryCodeFormatter {
    private static final Pattern PADDED_NUMERIC_SUFFIX = Pattern.compile("^([A-Z]+-)?0+(\\d+)$");

    private InventoryCodeFormatter() { }

    public static String compact(String code) {
        if (code == null) return null;
        Matcher matcher = PADDED_NUMERIC_SUFFIX.matcher(code);
        if (!matcher.matches()) return code;
        String prefix = matcher.group(1) == null ? "" : matcher.group(1);
        return prefix + new BigInteger(matcher.group(2));
    }
}
