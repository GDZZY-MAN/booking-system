package cn.shopbooking.shop;

import cn.shopbooking.web.ApiException;
import cn.shopbooking.web.Inputs;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopService {
    public record Shop(long id, String slug, String name, String description, boolean published) {}
    public record Offering(long id, String name, int durationMinutes, boolean active) {}
    public record Details(String name, String description) {}
    public record OfferingInput(String name, int durationMinutes, Boolean active) {}

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
        return db.sql("select * from shop where slug = ? and published").param(slug).query(Shop.class)
                .optional().orElseThrow(ShopService::notPublished);
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
        db.sql("update shop set published = ? where id = ?").params(value, shopId).update();
    }

    public List<Offering> offerings(long shopId) {
        return db.sql("select id, name, duration_minutes, active from service where shop_id = ? order by id")
                .param(shopId).query(Offering.class).list();
    }

    public Offering activeOffering(long shopId, long id) {
        return db.sql("select id, name, duration_minutes, active from service where shop_id = ? and id = ? and active")
                .params(shopId, id).query(Offering.class).optional()
                .orElseThrow(() -> ApiException.missing("这项服务当前不可预约。"));
    }

    @Transactional
    public Offering saveOffering(long shopId, Long id, OfferingInput input) {
        String name = Inputs.required(input.name(), 80, "服务名称");
        int duration = input.durationMinutes();
        if (duration < 15 || duration > 480 || duration % 15 != 0 || input.active() == null) {
            throw ApiException.invalid("服务时长须为 15 至 480 分钟，且是 15 的倍数。");
        }
        lockConfiguration(shopId);
        if (id == null) {
            return db.sql("""
                    insert into service(shop_id, name, duration_minutes, active) values (?, ?, ?, ?)
                    returning id, name, duration_minutes, active
                    """).params(shopId, name, duration, input.active()).query(Offering.class).single();
        }
        return db.sql("""
                update service set name = ?, duration_minutes = ?, active = ? where shop_id = ? and id = ?
                returning id, name, duration_minutes, active
                """).params(name, duration, input.active(), shopId, id).query(Offering.class).optional()
                .orElseThrow(() -> ApiException.missing("服务不存在。"));
    }
}
