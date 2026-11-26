package cn.shopbooking.shop;

import cn.shopbooking.library.Libraries;
import cn.shopbooking.library.SeatLifecycle;
import cn.shopbooking.library.SeatReservations;
import cn.shopbooking.schedule.ScheduleService;
import cn.shopbooking.web.ApiException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOperations {
    public record Merchant(long id, String slug, String name, String description, String merchantState,
                           boolean published, String reviewNote, String ownerUsername, String category, String address) {}
    public record Venue(long id, String slug, String name, boolean published, String spaceType, int seatCount) {}
    public record NewVenue(String name, String slug) {}
    public record Decision(String action, String note) {}
    private final JdbcClient db;
    private final ShopService shops;
    private final ScheduleService schedules;
    private final Libraries libraries;
    private final SeatLifecycle lifecycle;
    private final SeatReservations reservations;
    public PlatformOperations(JdbcClient db, ShopService shops, ScheduleService schedules, Libraries libraries,
                              SeatLifecycle lifecycle, SeatReservations reservations) {
        this.lifecycle = lifecycle;
        this.reservations = reservations;
        this.db = db; this.shops = shops; this.schedules = schedules; this.libraries = libraries;
    }
    public record Cover(String cover) {}
    public Cover cover(long id) {
        requireVenue(id);
        return new Cover(db.sql("select cover from library where shop_id = ?").param(id).query(String.class).single());
    }
    @Transactional
    public Cover saveCover(long id, Cover input) {
        lockEditableVenue(id);
        String cover = ShopBranding.image(input.cover(), 400, "场馆封面");
        db.sql("update library set cover = ? where shop_id = ?").params(cover, id).update();
        return new Cover(cover);
    }
    public record Summary(int pending, int serviceToday, int seatsToday, int openVenues, int closedVenues,
                          List<VenueTask> venueTasks) {}
    public record VenueTask(long id, String name) {}
    public Summary summary(java.time.OffsetDateTime now) {
        var start = now.atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"));
        var end = start.plusDays(1).toOffsetDateTime();
        var begin = start.toOffsetDateTime();
        int pending = db.sql("select count(*) from shop where resource_kind = 'MERCHANT' and merchant_state = 'PENDING'").query(Integer.class).single();
        int serviceToday = db.sql("select count(*) from booking where status = 'CONFIRMED' and starts_at >= ? and starts_at < ?")
                .params(begin, end).query(Integer.class).single();
        int seatsToday = db.sql("select count(*) from seat_reservation r join library l on l.shop_id = r.shop_id where l.deleted_at is null and r.status <> 'CANCELLED' and r.starts_at >= ? and r.starts_at < ?")
                .params(begin, end).query(Integer.class).single();
        var venues = venues();
        int open = (int) venues.stream().filter(Venue::published).count();
        var tasks = db.sql("""
                select s.id, s.name from shop s join library l on l.shop_id = s.id left join campus_profile p on p.shop_id = s.id
                where s.resource_kind = 'CAMPUS' and l.deleted_at is null and (coalesce(p.address,'') = '' or coalesce(p.rules,'') = ''
                    or not exists(select 1 from library_seat x where x.shop_id = s.id and x.active)) order by s.id
                """).query(VenueTask.class).list();
        return new Summary(pending, serviceToday, seatsToday, open, venues.size() - open, tasks);
    }
    public record MerchantSetup(List<ShopService.Offering> services, List<ScheduleService.Employee> employees) {}
    public MerchantSetup merchantSetup(long id) {
        requireMerchant(id);
        return new MerchantSetup(shops.offerings(id), schedules.employees(id));
    }
    public List<Merchant> merchants() {
        return db.sql("""
                select s.id, s.slug, s.name, s.description, s.merchant_state, s.published, s.review_note,
                    coalesce(a.username, '') as owner_username, coalesce(p.category, 'OTHER') as category,
                    coalesce(p.address, '') as address from shop s
                left join account a on a.shop_id = s.id and a.role = 'OWNER'
                left join campus_profile p on p.shop_id = s.id
                where s.resource_kind = 'MERCHANT' order by case when s.merchant_state = 'PENDING' then 0 else 1 end, s.id desc
                """).query(Merchant.class).list();
    }
    public void requireMerchant(long id) {
        if (!shops.isMerchant(id)) throw ApiException.missing("商家不存在。");
    }

    @Transactional
    public void archiveMerchant(long id, String actor) {
        requireMerchant(id); shops.lockConfiguration(id);
        if (shops.get(id).merchantState().equals("DELETED")) return;
        int count = db.sql("select count(*) from booking where shop_id = ? and status = 'CONFIRMED' and ends_at > ?")
                .params(id, lifecycle.now()).query(Integer.class).single();
        if (count > 0) throw ApiException.conflict("MERCHANT_HAS_RESERVATIONS", "还有 " + count + " 笔未结束预约，请先处理或等其结束后再删除。");
        db.sql("update shop set published = false, merchant_state = 'DELETED' where id = ?").param(id).update();
        db.sql("delete from employee_invite where employee_id in (select id from employee where shop_id = ?)").param(id).update();
        db.sql("insert into merchant_moderation_event(shop_id, actor, action, note) values (?, ?, 'delete', '移入商家回收站')")
                .params(id, actor).update();
    }

    @Transactional
    public void restoreMerchant(long id, String actor) {
        requireMerchant(id); shops.lockConfiguration(id);
        if (!shops.get(id).merchantState().equals("DELETED")) throw ApiException.conflict("STATE_CHANGED", "商家不在回收站中。");
        db.sql("update shop set published = false, merchant_state = 'DRAFT', review_note = '' where id = ?").param(id).update();
        db.sql("insert into merchant_moderation_event(shop_id, actor, action, note) values (?, ?, 'restore', '恢复后需重新提交审核')")
                .params(id, actor).update();
    }
    public void requireVenue(long id) {
        if (!db.sql("select exists(select 1 from shop s join library l on l.shop_id = s.id where s.id = ? and s.resource_kind = 'CAMPUS' and l.deleted_at is null)")
                .param(id).query(Boolean.class).single()) throw ApiException.missing("校园空间不存在。");
    }
    @Transactional
    public ShopService.Shop submit(long id, boolean publish) {
        requireMerchant(id); shops.lockConfiguration(id);
        var shop = shops.get(id);
        if (shop.merchantState().equals("DELETED")) throw ApiException.conflict("MERCHANT_REMOVED", "商家已删除，请联系管理员恢复。");
        if (!publish) {
            db.sql("update shop set published = false, merchant_state = case when merchant_state = 'PENDING' then 'DRAFT' else merchant_state end where id = ?").param(id).update();
        } else {
            if (shop.merchantState().equals("SUSPENDED")) throw ApiException.conflict("MERCHANT_SUSPENDED", "商家已被平台下架，请联系管理员处理，不能自行恢复发布。");
            if (!schedules.readyToPublish(id)) throw ApiException.conflict("SETUP_INCOMPLETE", "请先上架服务，安排员工与足够的排班时段。");
            if (shop.merchantState().equals("APPROVED")) {
                db.sql("update shop set published = true where id = ?").param(id).update();
            } else {
                db.sql("update shop set published = false, merchant_state = 'PENDING', review_note = '' where id = ?").param(id).update();
            }
        }
        return shops.get(id);
    }
    @Transactional
    public ShopService.Shop decide(long id, String actor, Decision input) {
        requireMerchant(id); shops.lockConfiguration(id);
        var shop = shops.get(id);
        String note = input.note() == null ? "" : input.note().strip();
        if (note.length() > 500 || note.indexOf(0) >= 0) throw ApiException.invalid("处理原因最多 500 字。");
        String next;
        boolean published;
        switch (input.action() == null ? "" : input.action()) {
            case "approve" -> {
                if (!shop.merchantState().equals("PENDING")) throw ApiException.conflict("STATE_CHANGED", "只有待审核商家可以通过审核。");
                if (!schedules.readyToPublish(id)) throw ApiException.conflict("SETUP_INCOMPLETE", "商家的服务或排班尚不完整，暂不能通过审核。");
                next = "APPROVED"; published = true;
            }
            case "reject" -> {
                if (!shop.merchantState().equals("PENDING")) throw ApiException.conflict("STATE_CHANGED", "只有待审核商家可以驳回。");
                if (note.isBlank()) throw ApiException.invalid("请填写驳回原因。");
                next = "REJECTED"; published = false;
            }
            case "suspend" -> {
                if (!shop.merchantState().equals("APPROVED")) throw ApiException.conflict("STATE_CHANGED", "只有审核通过的商家可以下架。");
                if (note.isBlank()) throw ApiException.invalid("请填写下架原因。");
                next = "SUSPENDED"; published = false;
            }
            case "resume" -> {
                if (!shop.merchantState().equals("SUSPENDED")) throw ApiException.conflict("STATE_CHANGED", "只有平台下架的商家可以恢复。");
                if (!schedules.readyToPublish(id)) throw ApiException.conflict("SETUP_INCOMPLETE", "商家的服务或排班尚不完整，暂不能恢复。");
                next = "APPROVED"; published = true;
            }
            default -> throw ApiException.invalid("请选择可用的管理操作。");
        }
        db.sql("update shop set merchant_state = ?, published = ?, review_note = ? where id = ?").params(next, published, note, id).update();
        db.sql("insert into merchant_moderation_event(shop_id, actor, action, note) values (?, ?, ?, ?)").params(id, actor, input.action(), note).update();
        return shops.get(id);
    }
    public void lockEditableVenue(long id) {
        lifecycle.lock();
        requireVenue(id);
    }

    @Transactional
    public void archiveVenue(long id) {
        lockEditableVenue(id);
        int count = reservations.unfinishedCount(id);
        if (count > 0) {
            throw ApiException.conflict("VENUE_HAS_RESERVATIONS", "还有 " + count + " 笔未结束预约。请先处理预约或等其结束。");
        }
        db.sql("update library set published = false, deleted_at = ? where shop_id = ?")
                .params(lifecycle.now(), id).update();
    }

    @Transactional
    public void restoreVenue(long id) {
        lifecycle.lock();
        int changed = db.sql("""
                update library l set deleted_at = null, published = false from shop s
                where s.id = l.shop_id and s.resource_kind = 'CAMPUS' and l.shop_id = ? and l.deleted_at is not null
                """).param(id).update();
        if (changed != 1) throw ApiException.missing("回收站中没有这个空间。");
    }

    public List<Venue> venues() { return venues(false); }

    public List<Venue> venues(boolean archived) {
        return db.sql("""
                select s.id, s.slug, s.name, l.published, coalesce(p.space_type, 'LIBRARY') as space_type,
                    (select count(*) from library_seat x where x.shop_id = s.id) as seat_count
                from shop s join library l on l.shop_id = s.id left join campus_profile p on p.shop_id = s.id
                where s.resource_kind = 'CAMPUS' and (l.deleted_at is not null) = ?
                order by l.deleted_at desc nulls last, s.id desc
                """).param(archived).query(Venue.class).list();
    }
    @Transactional
    public ShopService.Shop createVenue(NewVenue input) {
        long id = shops.create(input.name(), input.slug());
        db.sql("update shop set resource_kind = 'CAMPUS' where id = ?").param(id).update();
        libraries.save(id, new Libraries.Settings(false, 480, 1320, 120));
        return shops.get(id);
    }
}
