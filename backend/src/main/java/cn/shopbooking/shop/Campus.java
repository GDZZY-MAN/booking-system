package cn.shopbooking.shop;

import cn.shopbooking.web.ApiException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class Campus {
    public record Profile(String spaceType, String category, String quietLevel, String address, String rules) {}
    public record Listing(String slug, String name, String description, String category, String address,
                          int serviceCount, int staffCount, Double rating, int reviewCount,
                          String logo, String cover, Integer minPriceCents, boolean hasFree) {}
    private final JdbcClient db;
    public Campus(JdbcClient db) { this.db = db; }

    public Profile profile(long shop) {
        return db.sql("select space_type, category, quiet_level, address, rules from campus_profile where shop_id = ?")
                .param(shop).query(Profile.class).optional()
                .orElse(new Profile("LIBRARY", "OTHER", "UNSPECIFIED", "", ""));
    }

    public Profile save(long shop, Profile input) {
        if (!List.of("LIBRARY", "STUDY", "DISCUSSION").contains(input.spaceType() == null ? "" : input.spaceType())
                || !List.of("NAILS", "PRINT", "PHOTO", "CONSULT", "CLUB", "OTHER").contains(input.category() == null ? "" : input.category())
                || !List.of("QUIET", "SOFT", "DISCUSSION", "UNSPECIFIED").contains(input.quietLevel() == null ? "" : input.quietLevel())) {
            throw ApiException.invalid("请选择有效的空间类型、服务分类和安静程度。");
        }
        String address = input.address() == null ? "" : input.address().strip();
        String rules = input.rules() == null ? "" : input.rules().strip();
        if (address.length() > 180 || rules.length() > 500 || address.indexOf(0) >= 0 || rules.indexOf(0) >= 0) {
            throw ApiException.invalid("地点最多 180 字，使用规则最多 500 字。");
        }
        db.sql("""
                insert into campus_profile(shop_id, space_type, category, quiet_level, address, rules) values (?, ?, ?, ?, ?, ?)
                on conflict(shop_id) do update set space_type = excluded.space_type, category = excluded.category,
                    quiet_level = excluded.quiet_level, address = excluded.address, rules = excluded.rules
                """).params(shop, input.spaceType(), input.category(), input.quietLevel(), address, rules).update();
        return profile(shop);
    }

    public List<Listing> services() {
        return db.sql("""
                select s.slug, s.name, s.description, coalesce(p.category, 'OTHER') as category,
                    coalesce(nullif(p.address, ''), b.address, '') as address,
                    coalesce(b.logo, '') as logo, coalesce(b.cover, '') as cover,
                    (select min(price_cents) from service where shop_id = s.id and active) as min_price_cents,
                    exists(select 1 from service where shop_id = s.id and active and price_mode = 'FREE') as has_free,
                    (select count(*) from service v where v.shop_id = s.id and v.active) as service_count,
                    (select count(*) from employee e where e.shop_id = s.id and e.active) as staff_count,
                    (select avg(r.rating) from service_review r join booking o on o.id = r.booking_id where o.shop_id = s.id) as rating,
                    (select count(*) from service_review r join booking o on o.id = r.booking_id where o.shop_id = s.id) as review_count
                from shop s left join campus_profile p on p.shop_id = s.id
                left join shop_branding b on b.shop_id = s.id and b.published
                where s.published and s.resource_kind = 'MERCHANT' and s.merchant_state = 'APPROVED'
                    and exists(select 1 from service v where v.shop_id = s.id and v.active)
                order by s.name, s.id
                """).query(Listing.class).list();
    }
}
