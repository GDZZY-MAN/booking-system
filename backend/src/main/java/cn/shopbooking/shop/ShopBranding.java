package cn.shopbooking.shop;

import cn.shopbooking.web.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopBranding {
    public record Brand(String theme, String address, String businessNote, String bookingNote,
                        String logo, String cover) {}

    private static final Brand DEFAULT = new Brand("forest", "", "", "", "", "");
    private static final Set<String> THEMES = Set.of("forest", "plum", "ocean", "clay");
    private static final String FIELDS = "theme, address, business_note, booking_note, logo, cover";
    private final JdbcClient db;
    private final ShopService shops;

    public ShopBranding(JdbcClient db, ShopService shops) {
        this.db = db;
        this.shops = shops;
    }

    public Brand get(long shopId, boolean published) {
        return db.sql("select " + FIELDS + " from shop_branding where shop_id = ? and published = ?")
                .params(shopId, published).query(Brand.class).optional().orElse(DEFAULT);
    }

    @Transactional
    public Brand save(long shopId, Brand input, boolean publish) {
        if (input.theme() == null || !THEMES.contains(input.theme())) {
            throw ApiException.invalid("请选择可用的主题色。");
        }
        Brand brand = new Brand(input.theme(), text(input.address(), 180, "到店地址"),
                text(input.businessNote(), 300, "营业说明"), text(input.bookingNote(), 500, "预约须知"),
                image(input.logo(), 200, "店标"), image(input.cover(), 400, "封面"));
        shops.lockConfiguration(shopId);
        write(shopId, brand, false);
        if (publish) {
            write(shopId, brand, true);
        }
        return brand;
    }

    private void write(long shopId, Brand brand, boolean published) {
        db.sql("insert into shop_branding (shop_id, published, " + FIELDS + ") values (?, ?, ?, ?, ?, ?, ?, ?) "
                + """
                on conflict (shop_id, published) do update set
                    theme = excluded.theme, address = excluded.address, business_note = excluded.business_note,
                    booking_note = excluded.booking_note, logo = excluded.logo, cover = excluded.cover
                """).params(shopId, published, brand.theme(), brand.address(), brand.businessNote(),
                brand.bookingNote(), brand.logo(), brand.cover()).update();
    }

    private static String text(String value, int max, String field) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.length() > max || cleaned.indexOf(0) >= 0) {
            throw ApiException.invalid(field + "限 " + max + " 个字符。");
        }
        return cleaned;
    }

    private static String image(String value, int maxKiB, String field) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int limit = maxKiB * 1024;
        int comma = value.indexOf(',');
        String prefix = comma >= 0 && comma < 24 ? value.substring(0, comma) : "";
        String format = switch (prefix) {
            case "data:image/png;base64" -> "png";
            case "data:image/jpeg;base64" -> "jpeg";
            default -> "";
        };
        String error = field + "须为不超过 " + maxKiB + " KB、长宽各不超过 2400 像素的 JPG 或 PNG 图片。";
        if (format.isEmpty() || value.length() - comma - 1 > ((limit + 2) / 3) * 4) {
            throw ApiException.invalid(error);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(value.substring(comma + 1));
            if (bytes.length > limit) {
                throw ApiException.invalid(error);
            }
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) {
                    throw ApiException.invalid(error);
                }
                var reader = readers.next();
                try {
                    reader.setInput(stream, true, true);
                    if (!format.equalsIgnoreCase(reader.getFormatName()) || reader.getWidth(0) > 2400
                            || reader.getHeight(0) > 2400 || reader.read(0) == null) {
                        throw ApiException.invalid(error);
                    }
                } finally {
                    reader.dispose();
                }
            }
            return prefix + "," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException | IllegalArgumentException invalid) {
            throw ApiException.invalid(error);
        }
    }
}
