package cn.shopbooking.booking;

import cn.shopbooking.schedule.ScheduleService;
import cn.shopbooking.shop.ShopService;
import cn.shopbooking.web.ApiException;
import cn.shopbooking.web.Inputs;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Bookings {
    public record Slot(OffsetDateTime startsAt, OffsetDateTime endsAt) {}
    public record Create(long serviceId, long employeeId, OffsetDateTime startsAt,
                         String customerName, String contact, UUID requestKey) {}
    public record Order(UUID id, long serviceId, long employeeId, String serviceName, String employeeName,
                        OffsetDateTime startsAt, OffsetDateTime endsAt, String customerName, String contact,
                        String status) {}
    public record Receipt(UUID id, String serviceName, String employeeName, OffsetDateTime startsAt,
                          OffsetDateTime endsAt, String status) {}
    public record MyBooking(UUID id, String slug, String venueName, String serviceName, String employeeName,
                            OffsetDateTime startsAt, OffsetDateTime endsAt, String status, String address, Integer rating) {}
    public record Review(int rating, String body) {}
    public record PublicReview(int rating, String body, OffsetDateTime createdAt, String serviceName) {}
    private static final String ORDER_FIELDS = """
            id, service_id, employee_id, service_name, employee_name,
            starts_at, ends_at, customer_name, contact, status
            """;
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int BOOKING_DAYS = 30;
    private static final int STEP_MINUTES = 15;

    private final JdbcClient db;
    private final ShopService shops;
    private final ScheduleService schedules;
    private final Clock clock;

    public Bookings(JdbcClient db, ShopService shops, ScheduleService schedules, Clock clock) {
        this.db = db;
        this.shops = shops;
        this.schedules = schedules;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Slot> available(String slug, long serviceId, long employeeId, LocalDate day) {
        var shop = shops.published(slug);
        var service = shops.activeOffering(shop.id(), serviceId);
        requireEligible(shop.id(), serviceId, employeeId);
        OffsetDateTime now = now();
        validateDay(day, now.toLocalDate());
        var reserved = db.sql("""
                select starts_at, ends_at from booking
                where employee_id = ? and status = 'CONFIRMED' and starts_at < ? and ends_at > ?
                """).params(employeeId, atMinute(day, 1440), atMinute(day, 0)).query(Slot.class).list();
        List<Slot> result = new ArrayList<>();
        for (var shift : schedules.shiftsFor(employeeId, day)) {
            for (int minute = shift.startMinute(); minute + service.durationMinutes() <= shift.endMinute(); minute += STEP_MINUTES) {
                var start = atMinute(day, minute);
                var end = start.plusMinutes(service.durationMinutes());
                if (start.isAfter(now) && reserved.stream().noneMatch(slot ->
                        start.isBefore(slot.endsAt()) && end.isAfter(slot.startsAt()))) {
                    result.add(new Slot(start, end));
                }
            }
        }
        return result;
    }

    @Transactional
    public Receipt create(String slug, Create input, long readerId) {
        String customer = Inputs.required(input.customerName(), 60, "称呼");
        String contact = Inputs.required(input.contact(), 80, "联系方式");
        if (input.requestKey() == null || input.startsAt() == null) {
            throw ApiException.invalid("请选择时段并重新提交。");
        }
        var shop = shops.lockBySlug(slug);

        // Serialize retries of a single request; unrelated bookings still run concurrently.
        db.sql("select 1 from pg_advisory_xact_lock(hashtextextended(?, 0))")
                .param(shop.id() + ":" + input.requestKey()).query(Integer.class).single();
        var previous = db.sql("select " + ORDER_FIELDS + " from booking where shop_id = ? and request_key = ?")
                .params(shop.id(), input.requestKey()).query(Order.class).optional();
        if (previous.isPresent()) {
            Order order = previous.get();
            if (db.sql("select count(*) from booking where id = ? and reader_id = ?")
                    .params(order.id(), readerId).query(Integer.class).single() != 1) {
                throw ApiException.conflict("REQUEST_KEY_REUSED", "请重新提交预约。");
            }
            if (order.serviceId() != input.serviceId() || order.employeeId() != input.employeeId()
                    || !order.startsAt().isEqual(input.startsAt()) || !order.customerName().equals(customer)
                    || !order.contact().equals(contact)) {
                throw ApiException.conflict("REQUEST_KEY_REUSED", "预约内容已变更，请重新提交。");
            }
            return receipt(order);
        }

        if (!shop.published() || !"MERCHANT".equals(shop.resourceKind()) || !"APPROVED".equals(shop.merchantState())) {
            throw ShopService.notPublished();
        }
        var service = shops.activeOffering(shop.id(), input.serviceId());
        var employee = requireEligible(shop.id(), service.id(), input.employeeId());
        OffsetDateTime start = input.startsAt().atZoneSameInstant(SHOP_ZONE).toOffsetDateTime();
        OffsetDateTime now = now();
        LocalDate day = start.toLocalDate();
        validateDay(day, now.toLocalDate());
        int minute = start.getHour() * 60 + start.getMinute();
        if (!start.isAfter(now) || start.getSecond() != 0 || start.getNano() != 0
                || schedules.shiftsFor(employee.id(), day).stream().noneMatch(
                        shift -> minute >= shift.startMinute() && (minute - shift.startMinute()) % STEP_MINUTES == 0
                                && minute + service.durationMinutes() <= shift.endMinute())) {
            throw ApiException.conflict("SLOT_UNAVAILABLE", "这个时段已不可预约，请重新选择。");
        }
        Order order = db.sql("""
                insert into booking(id, shop_id, employee_id, service_id, service_name, employee_name,
                    starts_at, ends_at, customer_name, contact, status, request_key, reader_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, ?) returning
                """ + ORDER_FIELDS).params(UUID.randomUUID(), shop.id(), employee.id(), service.id(),
                service.name(), employee.name(), start, start.plusMinutes(service.durationMinutes()),
                customer, contact, input.requestKey(), readerId).query(Order.class).single();
        return receipt(order);
    }

    public List<MyBooking> mine(long reader) {
        return db.sql("""
                select o.id, s.slug, s.name as venue_name, o.service_name, o.employee_name, o.starts_at, o.ends_at,
                    o.status, coalesce(nullif(p.address, ''), b.address, '') as address, r.rating
                from booking o join shop s on s.id = o.shop_id
                left join campus_profile p on p.shop_id = s.id
                left join shop_branding b on b.shop_id = s.id and b.published
                left join service_review r on r.booking_id = o.id
                where o.reader_id = ? order by o.starts_at, o.id
                """).param(reader).query(MyBooking.class).list();
    }

    @Transactional
    public void cancelByReader(long reader, UUID id) {
        int changed = db.sql("""
                update booking set status = 'CANCELLED' where id = ? and reader_id = ? and starts_at > ? and status = 'CONFIRMED'
                """).params(id, reader, now()).update();
        if (changed != 1) throw ApiException.conflict("CANNOT_CANCEL", "仅能取消自己尚未开始的服务预约，请刷新安排。");
    }

    @Transactional
    public void review(long reader, UUID id, Review input) {
        String body = Inputs.required(input.body(), 500, "评价内容");
        if (input.rating() < 1 || input.rating() > 5) throw ApiException.invalid("请选择 1–5 星评分。");
        var order = db.sql("select " + ORDER_FIELDS + " from booking where id = ? and reader_id = ? for update")
                .params(id, reader).query(Order.class).optional().orElseThrow(() -> ApiException.missing("预约不存在。"));
        if (!order.status().equals("CONFIRMED") || order.endsAt().isAfter(now())) {
            throw ApiException.conflict("REVIEW_NOT_READY", "服务结束后才能评价，已取消的预约不能评价。");
        }
        if (db.sql("insert into service_review(booking_id, rating, body, created_at) values (?, ?, ?, ?) on conflict do nothing")
                .params(id, input.rating(), body, now()).update() == 0) {
            throw ApiException.conflict("ALREADY_REVIEWED", "这次服务已经评价过了。");
        }
    }

    public List<PublicReview> reviews(String slug) {
        long shop = shops.published(slug).id();
        return db.sql("""
                select r.rating, r.body, r.created_at, o.service_name from service_review r
                join booking o on o.id = r.booking_id where o.shop_id = ? order by r.created_at desc limit 30
                """).param(shop).query(PublicReview.class).list();
    }

    public List<Order> today(long shopId) {
        LocalDate day = now().toLocalDate();
        return list(shopId, null, day, day);
    }

    public List<Order> list(long shopId, Long employeeId, LocalDate from, LocalDate through) {
        LocalDate start = from == null ? now().toLocalDate() : from;
        LocalDate end = through == null ? start.plusDays(30) : through;
        if (start.isBefore(LocalDate.of(1900, 1, 1)) || end.isAfter(LocalDate.of(2200, 12, 31))
                || end.isBefore(start) || end.isAfter(start.plusDays(30))) {
            throw ApiException.invalid("查询日期范围最多 31 天。");
        }
        String query = "select " + ORDER_FIELDS + " from booking where shop_id = :shop"
                + " and starts_at >= :start and starts_at < :end";
        if (employeeId != null) {
            query += " and employee_id = :employee";
        }
        var statement = db.sql(query + " order by starts_at, id").param("shop", shopId)
                .param("start", atMinute(start, 0)).param("end", atMinute(end.plusDays(1), 0));
        if (employeeId != null) {
            statement.param("employee", employeeId);
        }
        return statement.query(Order.class).list();
    }

    public Order cancel(long shopId, UUID id) {
        return db.sql("update booking set status = 'CANCELLED' where shop_id = ? and id = ? returning " + ORDER_FIELDS)
                .params(shopId, id).query(Order.class).optional()
                .orElseThrow(() -> ApiException.missing("预约不存在。"));
    }

    private ScheduleService.Member requireEligible(long shopId, long serviceId, long employeeId) {
        return schedules.eligible(shopId, serviceId).stream().filter(member -> member.id() == employeeId)
                .findFirst().orElseThrow(() -> ApiException.missing("该员工目前无法提供这项服务。"));
    }

    private static Receipt receipt(Order order) {
        return new Receipt(order.id(), order.serviceName(), order.employeeName(), order.startsAt(),
                order.endsAt(), order.status());
    }

    private OffsetDateTime now() {
        return clock.instant().atZone(SHOP_ZONE).toOffsetDateTime();
    }

    private static OffsetDateTime atMinute(LocalDate day, int minute) {
        return day.atStartOfDay(SHOP_ZONE).plusMinutes(minute).toOffsetDateTime();
    }

    private static void validateDay(LocalDate day, LocalDate today) {
        if (day.isBefore(today) || !day.isBefore(today.plusDays(BOOKING_DAYS))) {
            throw ApiException.invalid("请选择今天起 30 天内的日期。");
        }
    }
}
