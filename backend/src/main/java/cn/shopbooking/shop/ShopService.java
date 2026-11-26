package cn.shopbooking.shop;

import cn.shopbooking.web.ApiException;
import cn.shopbooking.web.Inputs;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopService {
    public record Shop(long id, String slug, String name, String description, boolean published, String resourceKind, String merchantState, String reviewNote) {}
    public record Offering(long id, String name, int durationMinutes, boolean active, String priceMode, Integer priceCents) {}
    public record Details(String name, String description) {}
    public record OfferingInput(String name, int durationMinutes, Boolean active, String priceMode, Integer priceCents) {
        public OfferingInput(String name, int durationMinutes, Boolean active) {
            this(name, durationMinutes, active, "INQUIRE", null);
        }
    }

    private final JdbcClient db;

    public ShopService(JdbcClient db) {
        this.db = db;
    }

    public long create(String name, String slug) {
        String shopName = Inputs.required(name, 80, "店铺名称");
        if (slug == null || !slug.matches("[a-z0-9][a-z0-9-]{1,58}[a-z0-9]")) {
            throw ApiException.invalid("预约链接标识须为 3–60 位小写字母、数字或连字符，首尾用字母或数字。");
        }
        return db.sql("insert into shop(name, slug) values (?, ?) returning id")
                .params(shopName, slug).query(Long.class).single();
    }

    public Shop get(long id) {
        return db.sql("select * from shop where id = ?").param(id).query(Shop.class).optional()
                .orElseThrow(() -> ApiException.missing("店铺不存在。"));
    }

    public Shop published(String slug) {
        return db.sql("select * from shop where slug = ? and published and resource_kind = 'MERCHANT' and merchant_state = 'APPROVED'").param(slug).query(Shop.class)
                .optional().orElseThrow(ShopService::notPublished);
    }

    public boolean isMerchant(long id) {
        return db.sql("select exists(select 1 from shop where id = ? and resource_kind = 'MERCHANT')").param(id).query(Boolean.class).single();
    }

    public boolean isActiveMerchant(long id) {
        return db.sql("select exists(select 1 from shop where id = ? and resource_kind = 'MERCHANT' and merchant_state <> 'DELETED')")
                .param(id).query(Boolean.class).single();
    }

    // Configuration updates take an exclusive lock. Reservations share it while validating.
    public void lockConfiguration(long id) {
        db.sql("select id from shop where id = ? for no key update").param(id).query(Long.class).single();
    }

    public Shop lockBySlug(String slug) {
        return db.sql("select * from shop where slug = ? for share").param(slug).query(Shop.class)
                .optional().orElseThrow(ShopService::notPublished);
    }

    public static ApiException notPublished() {
        return ApiException.missing("这个预约页面尚未发布或已经停用。");
    }

    @Transactional
    public Shop update(long shopId, Details input) {
        String name = Inputs.required(input.name(), 80, "店铺名称");
        String description = input.description() == null ? "" : input.description().strip();
        if (description.length() > 500 || description.indexOf(0) >= 0) {
            throw ApiException.invalid("店铺介绍限 500 个字符。");
        }
        lockConfiguration(shopId);
        db.sql("update shop set name = ?, description = ? where id = ?")
                .params(name, description, shopId).update();
        return get(shopId);
    }

    public void setPublished(long shopId, boolean value) {
        db.sql("update shop set published = ?, merchant_state = case when ? then 'APPROVED' else merchant_state end where id = ?").params(value, value, shopId).update();
    }

    public List<Offering> offerings(long shopId) {
        return db.sql("select * from service where shop_id = ? order by id")
                .param(shopId).query(Offering.class).list();
    }

    public Offering activeOffering(long shopId, long id) {
        return db.sql("select * from service where shop_id = ? and id = ? and active")
                .params(shopId, id).query(Offering.class).optional()
                .orElseThrow(() -> ApiException.missing("这项服务当前不可预约。"));
    }

    @Transactional
    public Offering saveOffering(long shopId, Long id, OfferingInput input) {
        String name = Inputs.required(input.name(), 80, "服务名称");
        int duration = input.durationMinutes();
        if (duration < 1 || duration > 480 || input.active() == null) {
            throw ApiException.invalid("服务时长须为 1 至 480 分钟。");
        }
        lockConfiguration(shopId);
        String mode = input.priceMode() == null ? "INQUIRE" : input.priceMode();
        Integer cents = input.priceCents();
        if (!List.of("FIXED", "FREE", "INQUIRE").contains(mode)
                || ("FIXED".equals(mode) ? cents == null || cents <= 0 : cents != null)) {
            throw ApiException.invalid("请选择价格方式，固定价格必须大于零。");
        }
        if (id == null) {
            return db.sql("""
                    insert into service(shop_id, name, duration_minutes, active, price_mode, price_cents)
                    values (?, ?, ?, ?, ?, ?) returning *
                    """).params(java.util.Arrays.asList(shopId, name, duration, input.active(), mode, cents)).query(Offering.class).single();
        }
        return db.sql("""
                update service set name = ?, duration_minutes = ?, active = ?, price_mode = ?, price_cents = ?
                where shop_id = ? and id = ? returning *
                """).params(java.util.Arrays.asList(name, duration, input.active(), mode, cents, shopId, id)).query(Offering.class).optional()
                .orElseThrow(() -> ApiException.missing("服务不存在。"));
    }
}
