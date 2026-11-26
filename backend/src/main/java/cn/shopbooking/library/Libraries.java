package cn.shopbooking.library;

import cn.shopbooking.shop.ShopService;
import cn.shopbooking.web.ApiException;
import cn.shopbooking.web.Inputs;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Libraries {
    public record Settings(boolean published, int openMinute, int closeMinute, int slotMinutes) {}
    public record Venue(long shopId, String slug, String name, String description, int openMinute,
                        int closeMinute, int slotMinutes, String spaceType, String quietLevel, String address,
                        String rules, int seatCount, int windowCount, int powerCount, String floors, String cover) {}
    public record Seat(long id, String label, String area, boolean nearWindow, boolean powerOutlet, boolean active,
                       Integer floor, Integer mapRow, Integer mapColumn) {}
    public record SeatInput(String label, String area, Boolean nearWindow, Boolean powerOutlet, Boolean active,
                            Integer floor, Integer mapRow, Integer mapColumn) {
        public SeatInput(String label, String area, Boolean nearWindow, Boolean powerOutlet, Boolean active) {
            this(label, area, nearWindow, powerOutlet, active, null, null, null);
        }
    }
    public record Slot(OffsetDateTime startsAt, OffsetDateTime endsAt) {}
    public record SeatOption(long id, String label, String area, boolean nearWindow, boolean powerOutlet,
                             boolean available, Integer floor, Integer mapRow, Integer mapColumn) {}
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcClient db;
    private final ShopService shops;
    private final SeatLifecycle lifecycle;

    public Libraries(JdbcClient db, ShopService shops, SeatLifecycle lifecycle) {
        this.db = db;
        this.shops = shops;
        this.lifecycle = lifecycle;
    }

    public Settings settings(long shopId) {
        return db.sql("select published, open_minute, close_minute, slot_minutes from library where shop_id = ?")
                .param(shopId).query(Settings.class).optional().orElse(new Settings(false, 480, 1320, 120));
    }

    @Transactional
    public Settings save(long shopId, Settings input) {
        if (input.openMinute() < 0 || input.closeMinute() > 1440 || input.closeMinute() <= input.openMinute()
                || input.openMinute() % 30 != 0 || input.closeMinute() % 30 != 0
                || input.slotMinutes() < 30 || input.slotMinutes() > 240 || input.slotMinutes() % 30 != 0
                || input.closeMinute() - input.openMinute() < input.slotMinutes()) {
            throw ApiException.invalid("营业时间按半小时设置，须容纳至少一个 30–240 分钟的预约时段。");
        }
        lifecycle.lock();
        if (db.sql("select exists(select 1 from library where shop_id = ? and deleted_at is not null)")
                .param(shopId).query(Boolean.class).single()) throw ApiException.missing("空间已移入回收站。");
        if (input.published() && seats(shopId).stream().noneMatch(Seat::active)) {
            throw ApiException.conflict("SETUP_INCOMPLETE", "请先保存营业时间并添加可用座位。");
        }
        db.sql("""
                insert into library(shop_id, published, open_minute, close_minute, slot_minutes) values (?, ?, ?, ?, ?)
                on conflict (shop_id) do update set published = excluded.published, open_minute = excluded.open_minute,
                    close_minute = excluded.close_minute, slot_minutes = excluded.slot_minutes
                """).params(shopId, input.published(), input.openMinute(), input.closeMinute(), input.slotMinutes()).update();
        return input;
    }

    public List<Venue> directory() {
        return db.sql(VENUE_QUERY + " where l.published and l.deleted_at is null order by s.id desc").query(Venue.class).list();
    }

    public Venue venue(String slug) {
        return db.sql(VENUE_QUERY + " where l.published and l.deleted_at is null and s.slug = ?").param(slug).query(Venue.class).optional()
                .orElseThrow(() -> ApiException.missing("这个座位预约站尚未开放。"));
    }

    private static final String VENUE_QUERY = """
            select s.id as shop_id, s.slug, s.name, s.description, l.open_minute, l.close_minute, l.slot_minutes,
                coalesce(p.space_type, 'LIBRARY') as space_type, coalesce(p.quiet_level, 'UNSPECIFIED') as quiet_level,
                coalesce(p.address, '') as address, coalesce(p.rules, '') as rules, l.cover,
                (select count(*) from library_seat x where x.shop_id = s.id and x.active) as seat_count,
                (select count(*) from library_seat x where x.shop_id = s.id and x.active and x.near_window) as window_count,
                (select count(*) from library_seat x where x.shop_id = s.id and x.active and x.power_outlet) as power_count,
                coalesce((select string_agg(distinct x.floor::text, ', ' order by x.floor::text)
                    from library_seat x where x.shop_id = s.id and x.active), '') as floors
            from library l join shop s on s.id = l.shop_id left join campus_profile p on p.shop_id = s.id
            """;

    public List<Seat> seats(long shopId) {
        return db.sql("""
                select id, label, area, near_window, power_outlet, active, floor, map_row, map_column from library_seat
                where shop_id = ? order by area, label, id
                """).param(shopId).query(Seat.class).list();
    }

    private SeatInput validateSeat(SeatInput input) {
        if (input == null) throw ApiException.invalid("缺少座位信息。");
        String label = Inputs.required(input.label(), 30, "座位编号");
        String area = Inputs.required(input.area(), 50, "楼层 / 区域");
        if (input.nearWindow() == null || input.powerOutlet() == null || input.active() == null) {
            throw ApiException.invalid("请检查座位设置。");
        }
        if ((input.floor() != null && (input.floor() < 1 || input.floor() > 20))
                || (input.mapRow() == null) != (input.mapColumn() == null)
                || (input.mapRow() != null && (input.mapRow() < 1 || input.mapRow() > 20
                || input.mapColumn() < 1 || input.mapColumn() > 12))) {
            throw ApiException.invalid("楼层为 1–20，平面图行列请成对填写，行 1–20、列 1–12。");
        }
        return new SeatInput(label, area, input.nearWindow(), input.powerOutlet(), input.active(),
                input.floor(), input.mapRow(), input.mapColumn());
    }

    private void lockSeats(long shopId) {
        lifecycle.lock();
        if (!db.sql("select exists(select 1 from library where shop_id = ? and deleted_at is null)")
                .param(shopId).query(Boolean.class).single()) throw ApiException.missing("空间不存在或已移入回收站。");
    }

    @Transactional
    public Seat saveSeat(long shopId, Long id, SeatInput input) {
        lockSeats(shopId);
        return writeSeat(shopId, id, validateSeat(input));
    }

    // Keep historical seats without coordinates in their current automatic display positions.
    private Map<Integer, Set<Integer>> occupiedCells(List<Seat> seats) {
        Map<Integer, Set<Integer>> byFloor = new HashMap<>();
        for (var seat : seats) {
            var cells = byFloor.computeIfAbsent(seat.floor(), key -> new HashSet<>());
            if (seat.mapRow() != null) cells.add(cellIndex(seat.mapRow(), seat.mapColumn()));
        }
        Map<Integer, Integer> nextCell = new HashMap<>();
        for (var seat : seats) {
            if (seat.mapRow() != null) continue;
            var cells = byFloor.get(seat.floor());
            int index = nextCell.getOrDefault(seat.floor(), 0);
            while (!cells.add(index)) index++;
            nextCell.put(seat.floor(), index + 1);
        }
        return byFloor;
    }

    private static int cellIndex(int row, int column) { return (row - 1) * 12 + column - 1; }

    @Transactional
    public List<Seat> addBatch(long shopId, List<SeatInput> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 240) {
            throw ApiException.invalid("每批可添加 1–240 个座位。");
        }
        lockSeats(shopId);
        var existing = seats(shopId);
        var cells = occupiedCells(existing);
        var labels = new HashSet<String>();
        existing.forEach(seat -> labels.add(seat.label()));
        var batch = new ArrayList<SeatInput>(inputs.size());
        for (var raw : inputs) {
            var seat = validateSeat(raw);
            if (seat.floor() == null || seat.mapRow() == null) throw ApiException.invalid("批量排座需要楼层和位置。");
            String at = seat.floor() + " 楼第 " + seat.mapRow() + " 排第 " + seat.mapColumn() + " 列";
            if (!labels.add(seat.label())) throw ApiException.conflict("SEAT_LAYOUT_CONFLICT", at + " 的座号 " + seat.label() + " 重复。");
            if (!cells.computeIfAbsent(seat.floor(), key -> new HashSet<>()).add(cellIndex(seat.mapRow(), seat.mapColumn()))) {
                throw ApiException.conflict("SEAT_LAYOUT_CONFLICT", at + " 已有座位，请调整位置。");
            }
            batch.add(seat);
        }
        for (var seat : batch) writeSeat(shopId, null, seat);
        return seats(shopId);
    }

    private Seat writeSeat(long shopId, Long id, SeatInput input) {
        String label = input.label();
        String area = input.area();
        if (id == null) {
            return db.sql("""
                    insert into library_seat(shop_id, label, area, near_window, power_outlet, active, floor, map_row, map_column)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id, label, area, near_window, power_outlet, active, floor, map_row, map_column
                    """).params(java.util.Arrays.asList(shopId, label, area, input.nearWindow(), input.powerOutlet(),
                            input.active(), input.floor(), input.mapRow(), input.mapColumn()))
                    .query(Seat.class).single();
        }
        return db.sql("""
                update library_seat set label = ?, area = ?, near_window = ?, power_outlet = ?, active = ?, floor = ?, map_row = ?, map_column = ?
                where shop_id = ? and id = ? returning id, label, area, near_window, power_outlet, active, floor, map_row, map_column
                """).params(java.util.Arrays.asList(label, area, input.nearWindow(), input.powerOutlet(), input.active(),
                        input.floor(), input.mapRow(), input.mapColumn(), shopId, id))
                .query(Seat.class).optional().orElseThrow(() -> ApiException.missing("座位不存在。"));
    }

    public List<Slot> slots(String slug, LocalDate date) {
        return slots(venue(slug), date);
    }

    public List<Slot> slots(Venue venue, LocalDate date) {
        LocalDate today = lifecycle.now().atZoneSameInstant(ZONE).toLocalDate();
        if (date.isBefore(today) || !date.isBefore(today.plusDays(7))) {
            throw ApiException.invalid("请选择今天起 7 天内的日期。");
        }
        List<Slot> result = new ArrayList<>();
        for (int minute = venue.openMinute(); minute + venue.slotMinutes() <= venue.closeMinute(); minute += venue.slotMinutes()) {
            var start = date.atStartOfDay(ZONE).plusMinutes(minute).toOffsetDateTime();
            if (start.isAfter(lifecycle.now())) {
                result.add(new Slot(start, start.plusMinutes(venue.slotMinutes())));
            }
        }
        return result;
    }

    public Slot requireSlot(Venue venue, OffsetDateTime start) {
        if (start == null) {
            throw ApiException.invalid("请选择预约时段。");
        }
        return slots(venue, start.atZoneSameInstant(ZONE).toLocalDate()).stream()
                .filter(slot -> slot.startsAt().isEqual(start)).findFirst()
                .orElseThrow(() -> ApiException.conflict("SLOT_UNAVAILABLE", "时段已过期或营业时间已调整，请重新选择。"));
    }

    public List<SeatOption> available(String slug, OffsetDateTime start) {
        Venue venue = venue(slug);
        Slot slot = requireSlot(venue, start);
        return db.sql("""
                select s.id, s.label, s.area, s.near_window, s.power_outlet, s.floor, s.map_row, s.map_column,
                    not exists(select 1 from seat_reservation r where r.seat_id = s.id
                        and r.status in ('RESERVED', 'ACTIVE') and r.starts_at < ? and r.ends_at > ?) as available
                from library_seat s where s.shop_id = ? and s.active order by s.area, s.label, s.id
                """).params(slot.endsAt(), slot.startsAt(), venue.shopId()).query(SeatOption.class).list();
    }
}
