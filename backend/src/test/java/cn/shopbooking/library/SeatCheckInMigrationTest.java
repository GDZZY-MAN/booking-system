package cn.shopbooking.library;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SEAT_TEST_DB_URL", matches = ".+")
class SeatCheckInMigrationTest {
    @Test
    void upgradesLegacyReservationsWithoutLosingOccupancyOrKeepingPenalties() throws Exception {
        String schema = "seat_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("SEAT_TEST_DB_URL");
        String user = System.getenv().getOrDefault("SEAT_TEST_DB_USER", "shopbooking");
        String password = System.getenv().getOrDefault("SEAT_TEST_DB_PASSWORD", "");
        String scoped = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        try {
            Flyway.configure().dataSource(scoped, user, password).defaultSchema(schema).target("7").load().migrate();
            try (var connection = DriverManager.getConnection(scoped, user, password); var statement = connection.createStatement()) {
                statement.execute("insert into shop(slug, name) values ('upgrade', 'Upgrade fixture')");
                statement.execute("insert into library values (1, true, 480, 1200, 60)");
                statement.execute("""
                    insert into reader_account(username, password_hash, no_show_streak, blocked_until)
                    select 'upgrade-' || n, 'unused', 2, now() + interval '7 days' from generate_series(1, 4) n
                    """);
                statement.execute("""
                    insert into library_seat(shop_id, label, area) values (1, 'A', 'fixture'), (1, 'B', 'fixture'), (1, 'C', 'fixture')
                    """);
                statement.execute("""
                    insert into seat_reservation(id, shop_id, seat_id, reader_id, seat_label, area, starts_at, ends_at, status, request_key) values
                    (gen_random_uuid(),1,1,1,'A','fixture',now()-interval '10 minutes',now()+interval '50 minutes','CHECKED_IN',gen_random_uuid()),
                    (gen_random_uuid(),1,2,2,'B','fixture',now()+interval '1 hour',now()+interval '2 hours','RESERVED',gen_random_uuid()),
                    (gen_random_uuid(),1,1,3,'A','fixture',now()-interval '10 minutes',now()+interval '50 minutes','NO_SHOW',gen_random_uuid()),
                    (gen_random_uuid(),1,3,4,'C','fixture',now()-interval '2 hours',now()-interval '1 hour','RESERVED',gen_random_uuid())
                    """);
            }
            Flyway.configure().dataSource(scoped, user, password).defaultSchema(schema).load().migrate();
            try (var connection = DriverManager.getConnection(scoped, user, password); var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("select count(*), count(distinct status) from seat_reservation")) {
                    assertTrue(rows.next()); assertEquals(4, rows.getInt(1)); assertEquals(4, rows.getInt(2));
                }
                try (var rows = statement.executeQuery("select count(*) from reader_account where no_show_streak <> 0 or blocked_until is not null")) {
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                }
                SQLException collision = assertThrows(SQLException.class, () -> statement.execute("""
                    insert into seat_reservation(id,shop_id,seat_id,reader_id,seat_label,area,starts_at,ends_at,status,request_key)
                    values(gen_random_uuid(),1,1,3,'A','fixture',now(),now()+interval '30 minutes','RESERVED',gen_random_uuid())
                    """));
                assertEquals("23P01", collision.getSQLState());
                SQLException duplicate = assertThrows(SQLException.class, () -> statement.execute("""
                    insert into seat_reservation(id,shop_id,seat_id,reader_id,seat_label,area,starts_at,ends_at,status,request_key)
                    values(gen_random_uuid(),1,3,1,'C','fixture',now(),now()+interval '30 minutes','ACTIVE',gen_random_uuid())
                    """));
                assertEquals("23505", duplicate.getSQLState());
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                statement.execute("drop schema " + schema + " cascade");
            }
        }
    }
}
