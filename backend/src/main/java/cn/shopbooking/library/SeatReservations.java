package cn.shopbooking.library;

import cn.shopbooking.web.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatReservations {
    public record Create(long seatId, OffsetDateTime startsAt, UUID requestKey) {}
    public record Reservation(UUID id, String slug, String venueName, String seatLabel, String area,
                              OffsetDateTime startsAt, OffsetDateTime endsAt, String status, String username) {}
    public record ReaderState(String username) {}
    private record Saved(UUID id, long shopId, long seatId, OffsetDateTime startsAt) {}
    private static final String SELECT = """
            select r.id, s.slug, s.name as venue_name, r.seat_label, r.area, r.starts_at, r.ends_at, r.status, a.username
            from seat_reservation r join shop s on s.id = r.shop_id join reader_account a on a.id = r.reader_id
            """;
    private final JdbcClient db;
    private final Libraries libraries;
    private final SeatLifecycle lifecycle;

    public SeatReservations(JdbcClient db, Libraries libraries, SeatLifecycle lifecycle) {
        this.db = db;
        this.libraries = libraries;
        this.lifecycle = lifecycle;
    }

    public ReaderState reader(long reader) {
        return db.sql("select username from reader_account where id = ?")
                .param(reader).query(ReaderState.class).single();
    }

    @Transactional
    public Reservation create(long reader, String slug, Create input) {
        if (input.requestKey() == null || input.startsAt() == null) {
            throw ApiException.invalid("请选择座位和时段。");
        }
        lifecycle.lock();
        var previous = db.sql("select id, shop_id, seat_id, starts_at from seat_reservation where reader_id = ? and request_key = ?")
                .params(reader, input.requestKey()).query(Saved.class).optional();
        if (previous.isPresent()) {
            var row = previous.get();
            String savedSlug = db.sql("select slug from shop where id = ?").param(row.shopId()).query(String.class).single();
            if (!savedSlug.equals(slug) || row.seatId() != input.seatId() || !row.startsAt().isEqual(input.startsAt())) {
                throw ApiException.conflict("REQUEST_KEY_REUSED", "预约内容有变化，请重新提交。");
            }
            return get(row.id());
        }
        if (db.sql("select exists(select 1 from seat_reservation where reader_id = ? and status in ('RESERVED', 'ACTIVE'))")
                .param(reader).query(Boolean.class).single()) {
            throw ApiException.conflict("ACTIVE_RESERVATION_EXISTS", "你已有一笔有效预约，请先使用、取消或结束它。");
        }
        var venue = libraries.venue(slug);
        var slot = libraries.requireSlot(venue, input.startsAt());
        var seat = libraries.available(slug, slot.startsAt()).stream().filter(row -> row.id() == input.seatId())
                .findFirst().orElseThrow(() -> ApiException.missing("座位已停用或不存在。"));
        if (!seat.available()) {
            throw ApiException.conflict("SLOT_UNAVAILABLE", "座位刚被预约，请重新选座。");
        }
        UUID id = UUID.randomUUID();
        db.sql("""
                insert into seat_reservation(id, shop_id, seat_id, reader_id, seat_label, area,
                    starts_at, ends_at, status, request_key) values (?, ?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?)
                """).params(id, venue.shopId(), seat.id(), reader, seat.label(), seat.area(),
                slot.startsAt(), slot.endsAt(), input.requestKey()).update();
        return get(id);
    }

    public List<Reservation> mine(long reader) {
        return db.sql(SELECT + " where r.reader_id = ? order by r.created_at desc limit 100")
                .param(reader).query(Reservation.class).list();
    }

    private static final String UNFINISHED = " where r.shop_id = ? and r.status in ('RESERVED','ACTIVE') and r.ends_at > ?";

    public List<Reservation> unfinished(long shopId) {
        return db.sql(SELECT + UNFINISHED + " order by r.starts_at, r.seat_label")
                .params(shopId, lifecycle.now()).query(Reservation.class).list();
    }

    public int unfinishedCount(long shopId) {
        return db.sql("select count(*) from seat_reservation r" + UNFINISHED)
                .params(shopId, lifecycle.now()).query(Integer.class).single();
    }

    public List<Reservation> forOwner(long shopId, java.time.LocalDate day) {
        var start = day.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
        return db.sql(SELECT + " where r.shop_id = ? and r.starts_at >= ? and r.starts_at < ? order by r.starts_at, r.seat_label")
                .params(shopId, start, start.plusDays(1)).query(Reservation.class).list();
    }

    @Transactional
    public Reservation readerAction(long reader, UUID id, String action) {
        lifecycle.lock();
        db.sql("select id from seat_reservation where reader_id = ? and id = ?").params(reader, id)
                .query(UUID.class).optional().orElseThrow(() -> ApiException.missing("预约不存在。"));
        Reservation row = get(id);
        if ("cancel".equals(action) && "CANCELLED".equals(row.status())) {
            return row;
        }
        if ("finish".equals(action) && "COMPLETED".equals(row.status())) {
            return row;
        }
        if ("cancel".equals(action) && "RESERVED".equals(row.status()) && row.startsAt().isAfter(lifecycle.now())) {
            update(id, "CANCELLED");
        } else if ("finish".equals(action) && List.of("RESERVED", "ACTIVE").contains(row.status())
                && !row.startsAt().isAfter(lifecycle.now()) && row.endsAt().isAfter(lifecycle.now())) {
            update(id, "COMPLETED");
        } else {
            throw ApiException.conflict("INVALID_RESERVATION_STATE", "开始前可取消，预约时段内可结束使用，请刷新查看当前状态。");
        }
        return get(id);
    }

    @Transactional
    public Reservation ownerAction(long shopId, UUID id, String action) {
        lifecycle.lock();
        db.sql("select reader_id from seat_reservation where shop_id = ? and id = ?")
                .params(shopId, id).query(Long.class).optional().orElseThrow(() -> ApiException.missing("预约不存在。"));
        Reservation row = get(id);
        if ("cancel".equals(action) && "CANCELLED".equals(row.status())) {
            return row;
        }
        if ("cancel".equals(action) && List.of("RESERVED", "ACTIVE").contains(row.status()) && row.endsAt().isAfter(lifecycle.now())) {
            update(id, "CANCELLED");
        } else {
            throw ApiException.conflict("INVALID_RESERVATION_STATE", "只能取消有效预约，请刷新查看当前状态。");
        }
        return get(id);
    }

    private Reservation get(UUID id) {
        return db.sql(SELECT + " where r.id = ?").param(id).query(Reservation.class).single();
    }

    private void update(UUID id, String status) {
        db.sql("update seat_reservation set status = ? where id = ?").params(status, id).update();
    }
}
