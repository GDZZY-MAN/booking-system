package cn.shopbooking.library;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatLifecycle {
    private final JdbcClient db;
    private final Clock clock;

    public SeatLifecycle(JdbcClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    // Seat mutations share a short database transaction lock across application instances.
    // The existing service-booking module uses its own locks.
    public void lock() {
        db.sql("select 1 from pg_advisory_xact_lock(91473021)").query(Integer.class).single();
    }

    public OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    @Scheduled(fixedDelayString = "${booking.seats.cleanup-ms:60000}", initialDelay = 60000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refresh() {
        lock();
        OffsetDateTime now = now();
        db.sql("update seat_reservation set status = 'COMPLETED' where status in ('RESERVED', 'ACTIVE') and ends_at <= ?")
                .param(now).update();
        db.sql("update seat_reservation set status = 'ACTIVE' where status = 'RESERVED' and starts_at <= ? and ends_at > ?")
                .params(now, now).update();
    }
}
