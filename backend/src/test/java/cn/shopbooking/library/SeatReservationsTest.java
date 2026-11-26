package cn.shopbooking.library;

import cn.shopbooking.account.Accounts;
import cn.shopbooking.account.Readers;
import cn.shopbooking.web.ApiException;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(SeatReservationsTest.TimeConfig.class)
@EnabledIfEnvironmentVariable(named = "SEAT_TEST_DB_URL", matches = ".+")
class SeatReservationsTest {
    private static final String SCHEMA = "seat_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("SEAT_TEST_DB_URL");
    private static final String USER = System.getenv().getOrDefault("SEAT_TEST_DB_USER", "shopbooking");
    private static final String PASSWORD = System.getenv().getOrDefault("SEAT_TEST_DB_PASSWORD", "");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("create schema " + SCHEMA);
        }
        properties.add("spring.datasource.url", () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public");
        properties.add("spring.datasource.username", () -> USER);
        properties.add("spring.datasource.password", () -> PASSWORD);
    }

    @AfterAll
    static void cleanup() throws Exception {
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("drop schema " + SCHEMA + " cascade");
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant = Instant.parse("2030-06-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
        void at(String value) { instant = Instant.parse(value); }
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }

    @Autowired Accounts accounts;
    @Autowired Readers readers;
    @Autowired Libraries libraries;
    @Autowired SeatReservations reservations;
    @Autowired SeatLifecycle lifecycle;
    @Autowired MutableClock clock;
    @Autowired cn.shopbooking.social.ReaderSocial social;
    @Autowired cn.shopbooking.shop.ShopService shops;
    @Autowired cn.shopbooking.schedule.ScheduleService schedules;
    @Autowired cn.shopbooking.booking.Bookings bookings;
    @Autowired cn.shopbooking.shop.Campus campus;
    @Autowired cn.shopbooking.shop.PlatformOperations platform;
    @Autowired cn.shopbooking.account.PlatformAdmins admins;
    @Autowired org.springframework.web.context.WebApplicationContext webContext;
    @Autowired org.springframework.security.web.FilterChainProxy springSecurityFilterChain;

    @Test
    void merchantRecycleBinPreservesHistoryAndBlocksPublication() {
        clock.at("2040-06-01T00:00:00Z");
        var owner = accounts.register(new Accounts.Register("archive-owner", "test-pass42", "回收站测试商家", "archive-merchant"));
        platform.archiveMerchant(owner.shopId(), "test-admin");
        assertEquals("DELETED", shops.get(owner.shopId()).merchantState());
        assertFalse(shops.isActiveMerchant(owner.shopId()));
        assertThrows(ApiException.class, () -> shops.published("archive-merchant"));
        assertThrows(ApiException.class, () -> accounts.login(new Accounts.Credentials("archive-owner", "test-pass42")));
        assertThrows(ApiException.class, () -> platform.submit(owner.shopId(), false));
        platform.restoreMerchant(owner.shopId(), "test-admin");
        assertEquals("DRAFT", shops.get(owner.shopId()).merchantState());
        assertFalse(shops.get(owner.shopId()).published());
        assertTrue(shops.isActiveMerchant(owner.shopId()));
        assertEquals(owner.shopId(), accounts.login(new Accounts.Credentials("archive-owner", "test-pass42")).shopId());
        var venue = platform.createVenue(new cn.shopbooking.shop.PlatformOperations.NewVenue("独立空间", "archive-venue-check"));
        assertThrows(ApiException.class, () -> platform.archiveMerchant(venue.id(), "test-admin"));
    }

    @Test
    void platformModerationMultiVenueIsolationAndHttpRoles() throws Exception {
        clock.at("2035-06-01T00:00:00Z");
        admins.provision("test.platform", "platform-test-pass42");
        var admin = admins.login(new Accounts.Credentials("test.platform", "platform-test-pass42"));
        assertEquals("ADMIN", admin.role());
        var owner = accounts.register(new Accounts.Register("platform-owner", "test-pass42", "待审核工作室", "moderation-shop"));
        assertThrows(ApiException.class, () -> admins.login(new Accounts.Credentials("platform-owner", "test-pass42")));
        var service = shops.saveOffering(owner.shopId(), null, new cn.shopbooking.shop.ShopService.OfferingInput("摄影", 30, true));
        var shifts = java.util.stream.IntStream.rangeClosed(1, 7).mapToObj(day -> new cn.shopbooking.schedule.ScheduleService.Shift(day, 540, 1080)).toList();
        var employee = schedules.save(owner.shopId(), null, new cn.shopbooking.schedule.ScheduleService.EmployeeInput("摄影师", true, java.util.List.of(service.id()), shifts, java.util.List.of()));
        assertEquals("PENDING", platform.submit(owner.shopId(), true).merchantState());
        assertThrows(ApiException.class, () -> shops.published("moderation-shop"));
        assertThrows(ApiException.class, () -> platform.decide(owner.shopId(), admin.username(), new cn.shopbooking.shop.PlatformOperations.Decision("reject", "")));
        platform.decide(owner.shopId(), admin.username(), new cn.shopbooking.shop.PlatformOperations.Decision("reject", "请补充介绍"));
        assertEquals("REJECTED", shops.get(owner.shopId()).merchantState());
        platform.submit(owner.shopId(), true);
        platform.decide(owner.shopId(), admin.username(), new cn.shopbooking.shop.PlatformOperations.Decision("approve", "资料齐全"));
        var input = new cn.shopbooking.booking.Bookings.Create(service.id(), employee.id(), java.time.OffsetDateTime.parse("2035-06-01T09:00:00+08:00"), "测试顾客", "fixture-contact", UUID.randomUUID());
        long reader = readers.register(new Accounts.Credentials("moderation-reader", "test-pass42")).readerId();
        var receipt = bookings.create("moderation-shop", input, reader);
        platform.decide(owner.shopId(), admin.username(), new cn.shopbooking.shop.PlatformOperations.Decision("suspend", "暂停受理新预约"));
        assertThrows(ApiException.class, () -> platform.submit(owner.shopId(), true));
        platform.submit(owner.shopId(), false);
        assertEquals("SUSPENDED", shops.get(owner.shopId()).merchantState());
        assertThrows(ApiException.class, () -> shops.published("moderation-shop"));
        assertTrue(campus.services().stream().noneMatch(row -> row.slug().equals("moderation-shop")));
        assertTrue(bookings.list(owner.shopId(), null, LocalDate.of(2035, 6, 1), LocalDate.of(2035, 6, 1)).stream().anyMatch(row -> row.id().equals(receipt.id())));
        var attempt = new cn.shopbooking.booking.Bookings.Create(service.id(), employee.id(), input.startsAt().plusHours(1), "测试顾客", "fixture-contact", UUID.randomUUID());
        assertThrows(ApiException.class, () -> bookings.create("moderation-shop", attempt, reader));
        platform.decide(owner.shopId(), admin.username(), new cn.shopbooking.shop.PlatformOperations.Decision("resume", "已确认"));
        assertTrue(shops.published("moderation-shop").published());
        var first = platform.createVenue(new cn.shopbooking.shop.PlatformOperations.NewVenue("第一图书馆", "platform-library-one"));
        var second = platform.createVenue(new cn.shopbooking.shop.PlatformOperations.NewVenue("第二自习室", "platform-library-two"));
        assertEquals("CAMPUS", first.resourceKind());
        platform.requireVenue(first.id()); platform.requireVenue(second.id());
        assertThrows(ApiException.class, () -> platform.requireMerchant(first.id()));
        assertThrows(ApiException.class, () -> platform.requireVenue(owner.shopId()));
        var seat = libraries.saveSeat(first.id(), null, new Libraries.SeatInput("A-01", "一楼", true, true, true));
        assertTrue(libraries.seats(second.id()).isEmpty());
        assertThrows(ApiException.class, () -> libraries.saveSeat(second.id(), seat.id(), new Libraries.SeatInput("A-01", "一楼", false, false, true)));
        assertEquals(1, libraries.seats(first.id()).size());
        assertTrue(platform.venues().stream().anyMatch(row -> row.id() == second.id()));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).addFilters(springSecurityFilterChain).build();
        assertEquals(401, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/merchants")).andReturn().getResponse().getStatus());
        var merchantSession = authenticatedSession(owner);
        assertEquals(403, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/venues").session(merchantSession)).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/owner/shop").session(merchantSession)).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/venues/" + first.id() + "/seats").session(authenticatedSession(admin))).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/library-admin").session(merchantSession)).andReturn().getResponse().getStatus());
    }

    private org.springframework.mock.web.MockHttpSession authenticatedSession(Accounts.Viewer viewer) {
        var session = new org.springframework.mock.web.MockHttpSession();
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(viewer, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + viewer.role()))));
        session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test
    void campusDirectoriesAndAccountBoundServiceArrangements() {
        clock.at("2034-06-01T00:00:00Z");
        var owner = accounts.register(new Accounts.Register("campus-owner", "test-pass42", "校园影像", "campus-photo"));
        long a = readers.register(new Accounts.Credentials("campus-a", "test-pass42")).readerId();
        long b = readers.register(new Accounts.Credentials("campus-b", "test-pass42")).readerId();
        var service = shops.saveOffering(owner.shopId(), null, new cn.shopbooking.shop.ShopService.OfferingInput("证件照", 30, true));
        var shifts = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(day -> new cn.shopbooking.schedule.ScheduleService.Shift(day, 540, 1080)).toList();
        var staff = schedules.save(owner.shopId(), null, new cn.shopbooking.schedule.ScheduleService.EmployeeInput(
                "摄影师", true, java.util.List.of(service.id()), shifts, java.util.List.of()));
        var metadata = new cn.shopbooking.shop.Campus.Profile("STUDY", "PHOTO", "QUIET", "校园东门二楼", "使用后整理桌面。");
        assertEquals(metadata, campus.save(owner.shopId(), metadata));
        assertThrows(ApiException.class, () -> campus.save(owner.shopId(),
                new cn.shopbooking.shop.Campus.Profile("UNKNOWN", "PHOTO", "QUIET", "", "")));
        assertTrue(campus.services().stream().noneMatch(row -> row.slug().equals("campus-photo")));
        shops.setPublished(owner.shopId(), true);
        var listing = campus.services().stream().filter(row -> row.slug().equals("campus-photo")).findFirst().orElseThrow();
        assertEquals("PHOTO", listing.category());
        assertEquals(1, listing.serviceCount());
        assertEquals(1, listing.staffCount());
        assertEquals("校园东门二楼", listing.address());
        assertNull(listing.rating());
        libraries.save(owner.shopId(), new Libraries.Settings(false, 480, 1200, 60));
        libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("2-01", "二楼", true, true, true, 2, 1, 1));
        libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("2-02", "二楼", false, false, false, 2, 1, 2));
        libraries.save(owner.shopId(), new Libraries.Settings(true, 480, 1200, 60));
        var venue = libraries.venue("campus-photo");
        assertEquals("STUDY", venue.spaceType());
        assertEquals("QUIET", venue.quietLevel());
        assertEquals(1, venue.seatCount());
        assertEquals(1, venue.powerCount());
        assertEquals(1, venue.windowCount());
        assertEquals("2", venue.floors());
        var input = new cn.shopbooking.booking.Bookings.Create(service.id(), staff.id(),
                java.time.OffsetDateTime.parse("2034-06-01T09:00:00+08:00"), "同学", "fixture-contact", UUID.randomUUID());
        var owned = bookings.create("campus-photo", input, a);
        assertEquals(owned.id(), bookings.create("campus-photo", input, a).id());
        assertThrows(ApiException.class, () -> bookings.create("campus-photo", input, b));
        assertEquals(1, bookings.mine(a).size());
        assertTrue(bookings.mine(b).isEmpty());
        assertThrows(ApiException.class, () -> bookings.cancelByReader(b, owned.id()));
        var review = new cn.shopbooking.booking.Bookings.Review(4, "拍摄流程清楚，按时开始。");
        assertThrows(ApiException.class, () -> bookings.review(a, owned.id(), review));
        clock.at("2034-06-01T01:30:00Z");
        assertThrows(ApiException.class, () -> bookings.cancelByReader(a, owned.id()));
        assertThrows(ApiException.class, () -> bookings.review(b, owned.id(), review));
        bookings.review(a, owned.id(), review);
        assertThrows(ApiException.class, () -> bookings.review(a, owned.id(), review));
        assertEquals(4, bookings.mine(a).getFirst().rating());
        assertEquals(1, bookings.reviews("campus-photo").size());
        assertEquals(4.0, campus.services().stream().filter(row -> row.slug().equals("campus-photo")).findFirst().orElseThrow().rating());
        var future = bookings.create("campus-photo", new cn.shopbooking.booking.Bookings.Create(service.id(), staff.id(),
                java.time.OffsetDateTime.parse("2034-06-01T11:00:00+08:00"), "同学", "fixture-contact", UUID.randomUUID()), a);
        bookings.cancelByReader(a, future.id());
        clock.at("2034-06-01T04:00:00Z");
        assertThrows(ApiException.class, () -> bookings.review(a, future.id(), review));
        assertTrue(bookings.mine(a).stream().anyMatch(row -> row.id().equals(future.id()) && row.status().equals("CANCELLED")));
    }

    @Test
    void customMinutesAndMultipleClosedDaysRemainBookableAndPersist() {
        clock.at("2030-06-01T00:00:00Z");
        String unique = "custom-" + UUID.randomUUID();
        var owner = accounts.register(new Accounts.Register(unique, "test-password-123", "Custom minutes", unique));
        var service = shops.saveOffering(owner.shopId(), null,
                new cn.shopbooking.shop.ShopService.OfferingInput("Custom service", 37, true));
        var day = LocalDate.of(2030, 6, 3);
        var closed = java.util.List.of(day.plusDays(1), day.plusDays(2));
        var shifts = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(weekday -> new cn.shopbooking.schedule.ScheduleService.Shift(weekday, 547, 662)).toList();
        var employee = schedules.save(owner.shopId(), null,
                new cn.shopbooking.schedule.ScheduleService.EmployeeInput("Custom hours", true,
                        java.util.List.of(service.id()), shifts, closed));
        assertEquals(closed, schedules.employees(owner.shopId()).getFirst().closedDates());
        assertEquals(37, shops.offerings(owner.shopId()).getFirst().durationMinutes());
        shops.setPublished(owner.shopId(), true);
        var slots = bookings.available(unique, service.id(), employee.id(), day);
        assertFalse(slots.isEmpty());
        assertEquals("09:07", slots.getFirst().startsAt().toLocalTime().toString());
        assertTrue(slots.stream().allMatch(slot -> java.time.Duration.between(slot.startsAt(), slot.endsAt()).toMinutes() == 37));
        for (var closedDay : closed) assertTrue(bookings.available(unique, service.id(), employee.id(), closedDay).isEmpty());
        var first = slots.getFirst();
        long reader = readers.register(new Accounts.Credentials("custom-reader", "test-password-123")).readerId();
        assertThrows(ApiException.class, () -> bookings.create(unique,
                new cn.shopbooking.booking.Bookings.Create(service.id(), employee.id(), first.startsAt().plusMinutes(1),
                        "Reader", "test-contact", UUID.randomUUID()), reader));
        var receipt = bookings.create(unique, new cn.shopbooking.booking.Bookings.Create(service.id(), employee.id(),
                first.startsAt(), "Reader", "test-contact", UUID.randomUUID()), reader);
        assertTrue(first.endsAt().isEqual(receipt.endsAt()));
        assertFalse(bookings.available(unique, service.id(), employee.id(), day).stream()
                .anyMatch(slot -> slot.startsAt().isBefore(receipt.endsAt()) && slot.endsAt().isAfter(receipt.startsAt())));
        assertThrows(ApiException.class, () -> shops.saveOffering(owner.shopId(), null,
                new cn.shopbooking.shop.ShopService.OfferingInput("Invalid", 0, true)));
    }

    @Test
    void pricesAndCoversFollowTheirResource() {
        var owner = accounts.register(new Accounts.Register("priced-owner", "test-pass42", "价格测试", "price-fixture"));
        var service = shops.saveOffering(owner.shopId(), null, new cn.shopbooking.shop.ShopService.OfferingInput("打印", 10, true, "FIXED", 250));
        shops.setPublished(owner.shopId(), true);
        var listing = campus.services().stream().filter(item -> item.slug().equals("price-fixture")).findFirst().orElseThrow();
        assertEquals(250, listing.minPriceCents());
        shops.saveOffering(owner.shopId(), service.id(), new cn.shopbooking.shop.ShopService.OfferingInput("打印", 10, true, "FREE", null));
        assertTrue(campus.services().stream().anyMatch(item -> item.slug().equals("price-fixture") && item.hasFree() && item.minPriceCents() == null));
        assertThrows(ApiException.class, () -> shops.saveOffering(owner.shopId(), service.id(),
                new cn.shopbooking.shop.ShopService.OfferingInput("打印", 10, true, "FIXED", null)));
        var venue = platform.createVenue(new cn.shopbooking.shop.PlatformOperations.NewVenue("封面测试馆", "cover-fixture"));
        assertEquals("", platform.cover(venue.id()).cover());
        assertThrows(ApiException.class, () -> platform.saveCover(venue.id(), new cn.shopbooking.shop.PlatformOperations.Cover("bad")));
        assertThrows(ApiException.class, () -> platform.cover(owner.shopId()));
    }

    @Test
    void batchPlacementIsAtomicAndReservesExistingPositions() throws Exception {
        var space = platform.createVenue(new cn.shopbooking.shop.PlatformOperations.NewVenue("布局测试", "batch-layout"));
        long id = space.id();
        libraries.saveSeat(id, null, new Libraries.SeatInput("旧座位", "东区", false, false, true, 2, null, null));
        var first = new Libraries.SeatInput("2F-01", "东区", true, true, true, 2, 2, 1);
        var autoCell = new Libraries.SeatInput("2F-02", "东区", false, false, true, 2, 1, 1);
        assertEquals("SEAT_LAYOUT_CONFLICT", assertThrows(ApiException.class,
                () -> libraries.addBatch(id, java.util.List.of(first, autoCell))).code());
        assertThrows(ApiException.class, () -> libraries.addBatch(id, java.util.List.of(first, first)));
        assertThrows(ApiException.class, () -> libraries.addBatch(id, java.util.List.of(first,
                new Libraries.SeatInput("越界", "东区", false, false, true, 2, 20, 13))));
        assertEquals(1, libraries.seats(id).size());

        var barrier = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var attempts = java.util.stream.Stream.of(first,
                    new Libraries.SeatInput("并发座号", "西区", true, true, true, 2, 2, 1))
                    .map(seat -> workers.submit(() -> {
                        barrier.await();
                        try { libraries.addBatch(id, java.util.List.of(seat)); return 1; }
                        catch (ApiException error) { assertEquals("SEAT_LAYOUT_CONFLICT", error.code()); return 0; }
                    })).toList();
            barrier.countDown();
            assertEquals(1, attempts.getFirst().get(10, TimeUnit.SECONDS) + attempts.getLast().get(10, TimeUnit.SECONDS));
        }
        var placed = libraries.seats(id).stream().filter(s -> s.mapRow() != null).findFirst().orElseThrow();
        assertTrue(placed.nearWindow() && placed.powerOutlet());
        assertEquals(3, libraries.addBatch(id, java.util.List.of(
                new Libraries.SeatInput("追加", "东区", false, true, true, 2, 2, 2))).size());
    }

    @Test
    void recycleBinPreservesHistoryAndBlocksNewWorkUntilReopened() {
        clock.at("2037-06-01T00:00:00Z");
        var venue = platform.createVenue(new cn.shopbooking.shop.PlatformOperations.NewVenue("回收测试", "trash-fixture"));
        long id = venue.id();
        var seat = libraries.saveSeat(id, null, new Libraries.SeatInput("A", "一区", true, false, true));
        var hours = new Libraries.Settings(true, 480, 1200, 60);
        libraries.save(id, hours);
        long reader = readers.register(new Accounts.Credentials("trash-reader", "test-pass42")).readerId();
        var booked = reservations.create(reader, venue.slug(), request(seat.id(), "2037-06-03T10:00:00+08:00"));
        assertEquals("VENUE_HAS_RESERVATIONS", assertThrows(ApiException.class, () -> platform.archiveVenue(id)).code());
        assertEquals(booked.id(), reservations.unfinished(id).getFirst().id());
        clock.at("2037-06-03T02:30:00Z");
        lifecycle.refresh();
        assertEquals("ACTIVE", reservations.unfinished(id).getFirst().status());
        assertThrows(ApiException.class, () -> platform.archiveVenue(id));
        clock.at("2037-06-03T03:01:00Z");
        lifecycle.refresh();
        platform.archiveVenue(id);
        assertTrue(platform.venues().stream().noneMatch(row -> row.id() == id));
        assertTrue(platform.venues(true).stream().anyMatch(row -> row.id() == id));
        assertTrue(platform.summary(lifecycle.now()).venueTasks().stream().noneMatch(row -> row.id() == id));
        assertThrows(ApiException.class, () -> libraries.venue(venue.slug()));
        assertThrows(ApiException.class, () -> libraries.save(id, hours));
        assertThrows(ApiException.class, () -> platform.requireVenue(id));
        assertEquals("COMPLETED", reservations.mine(reader).getFirst().status());
        platform.restoreVenue(id);
        assertFalse(libraries.settings(id).published());
        assertEquals(seat.id(), libraries.seats(id).getFirst().id());
        assertThrows(ApiException.class, () -> libraries.venue(venue.slug()));
        libraries.save(id, hours);
        assertEquals(id, libraries.venue(venue.slug()).shopId());
    }

    @Test
    void archiveCannotRaceAnUnfinishedBooking() throws Exception {
        clock.at("2038-06-01T00:00:00Z");
        var venue = platform.createVenue(new cn.shopbooking.shop.PlatformOperations.NewVenue("并发测试", "trash-race"));
        var seat = libraries.saveSeat(venue.id(), null, new Libraries.SeatInput("A", "一区", true, false, true));
        libraries.save(venue.id(), new Libraries.Settings(true, 480, 1200, 60));
        long reader = readers.register(new Accounts.Credentials("race-reader", "test-pass42")).readerId();
        var barrier = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var archive = workers.submit(() -> {
                barrier.await();
                try { platform.archiveVenue(venue.id()); return true; }
                catch (ApiException e) { assertEquals("VENUE_HAS_RESERVATIONS", e.code()); return false; }
            });
            var book = workers.submit(() -> {
                barrier.await();
                try { reservations.create(reader, venue.slug(), request(seat.id(), "2038-06-02T10:00:00+08:00")); return true; }
                catch (ApiException e) { assertEquals("NOT_FOUND", e.code()); return false; }
            });
            barrier.countDown();
            boolean archived = archive.get(10, TimeUnit.SECONDS);
            assertNotEquals(archived, book.get(10, TimeUnit.SECONDS));
            assertEquals(archived, reservations.unfinished(venue.id()).isEmpty());
        }
    }

    private SeatReservations.Create request(long seat, String start) {
        return new SeatReservations.Create(seat, java.time.OffsetDateTime.parse(start), UUID.randomUUID());
    }

    @Test
    void concurrencyPermissionsAndAutomaticReservationLifecycle() throws Exception {
        clock.at("2030-06-01T00:00:00Z");
        var owner = accounts.register(new Accounts.Register("seat-owner", "test-pass42", "测试自习室", "seat-test"));
        var other = accounts.register(new Accounts.Register("other-owner", "test-pass42", "另一场所", "seat-other"));
        long first = readers.register(new Accounts.Credentials("reader-one", "test-pass42")).readerId();
        long second = readers.register(new Accounts.Credentials("reader-two", "test-pass42")).readerId();
        var config = new Libraries.Settings(false, 480, 1200, 60);
        libraries.save(owner.shopId(), config);
        var seatA = libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("A-01", "一楼", true, true, true, 1, 1, 1));
        var seatB = libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("A-02", "二楼", false, true, true, 2, 1, 1));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("DUP", "一楼", false, false, true, 1, 1, 1)));
        assertThrows(ApiException.class,
                () -> libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("INVALID", "一楼", false, false, true, 1, 1, null)));
        assertEquals(2, libraries.seats(owner.shopId()).get(1).floor());
        libraries.save(owner.shopId(), new Libraries.Settings(true, 480, 1200, 60));
        var start = "2030-06-01T09:00:00+08:00";
        var barrier = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { barrier.await(); return tryReserve(first, request(seatA.id(), start)); });
            var b = workers.submit(() -> { barrier.await(); return tryReserve(second, request(seatA.id(), start)); });
            barrier.countDown();
            assertEquals(1, a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS));
        }
        var won = reservations.forOwner(owner.shopId(), LocalDate.parse("2030-06-01")).getFirst();
        long winner = reservations.mine(first).isEmpty() ? second : first;
        long loser = winner == first ? second : first;
        assertThrows(ApiException.class, () -> reservations.readerAction(loser, won.id(), "cancel"));
        assertThrows(ApiException.class, () -> reservations.ownerAction(other.shopId(), won.id(), "check-in"));
        assertThrows(ApiException.class, () -> libraries.saveSeat(other.shopId(), seatA.id(),
                new Libraries.SeatInput("X", "三楼", false, false, false)));
        assertEquals("ACTIVE_RESERVATION_EXISTS", assertThrows(ApiException.class,
                () -> reservations.create(winner, "seat-test", request(seatB.id(), start))).code());
        assertEquals("INVALID_RESERVATION_STATE", assertThrows(ApiException.class,
                () -> reservations.ownerAction(owner.shopId(), won.id(), "check-in")).code());
        reservations.readerAction(winner, won.id(), "cancel");
        var sameReaderBarrier = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> {
                sameReaderBarrier.await();
                return tryReserve(first, request(seatA.id(), start), "ACTIVE_RESERVATION_EXISTS");
            });
            var b = workers.submit(() -> {
                sameReaderBarrier.await();
                return tryReserve(first, request(seatB.id(), start), "ACTIVE_RESERVATION_EXISTS");
            });
            sameReaderBarrier.countDown();
            assertEquals(1, a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS));
        }
        var active = reservations.mine(first).stream().filter(row -> row.status().equals("RESERVED")).findFirst().orElseThrow();
        reservations.readerAction(first, active.id(), "cancel");
        var retry = request(seatA.id(), start);
        var booked = reservations.create(first, "seat-test", retry);
        assertEquals(booked.id(), reservations.create(first, "seat-test", retry).id());
        assertEquals("REQUEST_KEY_REUSED", assertThrows(ApiException.class, () -> reservations.create(first, "seat-test",
                new SeatReservations.Create(seatB.id(), retry.startsAt(), retry.requestKey()))).code());
        reservations.create(second, "seat-test", request(seatB.id(), start));
        // Real PostgreSQL constraints remain active during simultaneous requests.
        clock.at("2030-06-01T01:15:00Z");
        lifecycle.refresh();
        assertEquals("ACTIVE", reservations.mine(first).getFirst().status());
        assertEquals("ACTIVE_RESERVATION_EXISTS", assertThrows(ApiException.class,
                () -> reservations.create(first, "seat-test", request(seatA.id(), "2030-06-01T10:00:00+08:00"))).code());
        clock.at("2030-06-01T02:00:00Z");
        lifecycle.refresh();
        assertEquals("COMPLETED", reservations.mine(first).getFirst().status());
        // Repeated reservations expire normally without a check-in action or suspension.
        for (int hour = 11; hour <= 15; hour += 2) {
            reservations.create(first, "seat-test", request(seatA.id(), "2030-06-01T" + hour + ":00:00+08:00"));
            clock.at("2030-06-01T" + String.format("%02d", hour - 8) + ":00:00Z");
            lifecycle.refresh();
            assertEquals("ACTIVE", reservations.mine(first).getFirst().status());
            clock.at("2030-06-01T" + String.format("%02d", hour - 7) + ":00:00Z");
            lifecycle.refresh();
            assertEquals("COMPLETED", reservations.mine(first).getFirst().status());
        }
        clock.at("2030-06-09T00:00:00Z");
        var checkin = reservations.create(first, "seat-test", request(seatA.id(), "2030-06-09T09:00:00+08:00"));
        clock.at("2030-06-09T01:00:00Z");
        lifecycle.refresh();
        assertEquals("ACTIVE", reservations.mine(first).getFirst().status());
        clock.at("2030-06-09T02:00:00Z");
        lifecycle.refresh();
        assertEquals("COMPLETED", reservations.mine(first).getFirst().status());
        var finish = reservations.create(first, "seat-test", request(seatA.id(), "2030-06-09T11:00:00+08:00"));
        clock.at("2030-06-09T03:00:00Z");
        lifecycle.refresh();
        assertEquals("COMPLETED", reservations.readerAction(first, finish.id(), "finish").status());
        assertEquals("COMPLETED", reservations.readerAction(first, finish.id(), "finish").status());
    }

    @Test
    void remindersConsentPrivacyAndSeatChanges() {
        clock.at("2031-06-01T00:00:00Z");
        var owner = accounts.register(new Accounts.Register("social-owner", "test-pass42", "交流测试馆", "social-test"));
        long a = readers.register(new Accounts.Credentials("social-a", "test-pass42")).readerId();
        long b = readers.register(new Accounts.Credentials("social-b", "test-pass42")).readerId();
        long c = readers.register(new Accounts.Credentials("social-c", "test-pass42")).readerId();
        libraries.save(owner.shopId(), new Libraries.Settings(false, 480, 1200, 60));
        var seatA = libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("S1", "阅读区", false, true, true));
        var seatB = libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("S2", "阅读区", false, true, true));
        var seatC = libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("S3", "阅读区", false, true, true));
        libraries.save(owner.shopId(), new Libraries.Settings(true, 480, 1200, 60));
        var bookingA = reservations.create(a, "social-test", request(seatA.id(), "2031-06-01T09:00:00+08:00"));
        var bookingB = reservations.create(b, "social-test", request(seatB.id(), "2031-06-01T09:00:00+08:00"));
        var bookingC = reservations.create(c, "social-test", request(seatC.id(), "2031-06-01T09:00:00+08:00"));
        var target = new cn.shopbooking.social.ReaderSocial.Target(seatB.id(), "VOICE", bookingB.id());
        assertEquals("RESERVATION_NOT_STARTED", assertThrows(ApiException.class, () -> social.remind(a, "social-test", target)).code());
        clock.at("2031-06-01T01:00:00Z");
        lifecycle.refresh();
        var visible = social.present(a, "social-test");
        assertFalse(visible.stream().filter(seat -> seat.id() == seatA.id()).findFirst().orElseThrow().available());
        var contact = visible.stream().filter(seat -> seat.id() == seatB.id()).findFirst().orElseThrow();
        assertEquals("同馆读者", contact.nickname());
        assertTrue(contact.chatEnabled());
        assertEquals(bookingB.id(), contact.reservationId());
        assertEquals("READER_CHANGED", assertThrows(ApiException.class, () -> social.remind(a, "social-test",
                new cn.shopbooking.social.ReaderSocial.Target(seatB.id(), "VOICE", UUID.randomUUID()))).code());
        social.remind(a, "social-test", target);
        social.remind(c, "social-test", target);
        assertEquals(1, social.notices(b).size());
        assertTrue(social.notices(a).isEmpty());
        var reminder = social.notices(b).getFirst();
        assertThrows(ApiException.class, () -> social.acknowledge(c, reminder.id()));
        social.acknowledge(b, reminder.id());
        assertTrue(social.notices(b).getFirst().acknowledged());
        social.preferences(b, new cn.shopbooking.social.ReaderSocial.Preferences("安静读者", false));
        var unavailable = social.present(a, "social-test").stream().filter(seat -> seat.id() == seatB.id()).findFirst().orElseThrow();
        assertEquals("安静读者", unavailable.nickname());
        assertFalse(unavailable.chatEnabled());
        assertEquals("CHAT_DISABLED", assertThrows(ApiException.class, () -> social.invite(a, "social-test", target)).code());
        social.preferences(b, new cn.shopbooking.social.ReaderSocial.Preferences("安静读者", true));
        var chat = social.invite(a, "social-test", target);
        assertEquals("同馆读者", chat.nickname());
        assertEquals(chat.id(), social.invite(a, "social-test", target).id());
        var text = new cn.shopbooking.social.ReaderSocial.Send("你好，一起学习吗？", UUID.randomUUID());
        assertThrows(ApiException.class, () -> social.send(a, chat.id(), text));
        assertThrows(ApiException.class, () -> social.action(a, chat.id(), "accept"));
        assertThrows(ApiException.class, () -> social.messages(c, chat.id()));
        social.action(b, chat.id(), "accept");
        var sent = social.send(a, chat.id(), text);
        assertEquals(sent.id(), social.send(a, chat.id(), text).id());
        assertEquals("安静读者", social.chats(a).getFirst().nickname());
        assertEquals(1, social.chats(b).getFirst().unread());
        social.seen(b, chat.id(), 0);
        assertEquals(1, social.chats(b).getFirst().unread());
        social.seen(b, chat.id(), sent.id());
        assertEquals(0, social.chats(b).getFirst().unread());
        // A later occupant of B's seat has no access to A and B's accepted chat.
        reservations.readerAction(b, bookingB.id(), "finish");
        assertThrows(ApiException.class, () -> social.remind(a, "social-test", target));
        social.send(a, chat.id(), new cn.shopbooking.social.ReaderSocial.Send("之后再交流", UUID.randomUUID()));
        assertEquals(2, social.messages(b, chat.id()).size());
        assertTrue(social.chats(c).isEmpty());
        var declined = social.invite(a, "social-test",
                new cn.shopbooking.social.ReaderSocial.Target(seatC.id(), null, bookingC.id()));
        social.action(c, declined.id(), "decline");
        var declinedState = social.chats(a).stream().filter(row -> row.id().equals(declined.id())).findFirst().orElseThrow();
        assertEquals("DECLINED", declinedState.status());
        assertEquals("RECIPIENT_DECLINED", declinedState.resolutionReason());
        social.action(a, declined.id(), "close");
        assertEquals("同馆读者", social.chats(a).stream().filter(row -> row.id().equals(declined.id()))
                .findFirst().orElseThrow().nickname());
        assertThrows(ApiException.class, () -> social.invite(a, "social-test",
                new cn.shopbooking.social.ReaderSocial.Target(seatC.id(), null, bookingC.id())));
        social.action(b, chat.id(), "block");
        var hidden = social.present(a, "social-test").stream().filter(seat -> seat.id() == seatB.id()).findFirst().orElseThrow();
        assertFalse(hidden.available());
        assertFalse(hidden.chatEnabled());
        assertNull(hidden.nickname());
        assertNull(hidden.reservationId());
        assertThrows(ApiException.class, () -> social.send(a, chat.id(),
                new cn.shopbooking.social.ReaderSocial.Send("被屏蔽后不能发送", UUID.randomUUID())));
    }

    @Test
    void futureReservationsCanChatAndBindToTheSelectedReaderOnASharedSeat() {
        clock.at("2032-06-01T00:00:00Z");
        var owner = accounts.register(new Accounts.Register("future-owner", "test-pass42", "提前交流测试馆", "future-social"));
        long a = readers.register(new Accounts.Credentials("future-a", "test-pass42")).readerId();
        long b = readers.register(new Accounts.Credentials("future-b", "test-pass42")).readerId();
        long c = readers.register(new Accounts.Credentials("future-c", "test-pass42")).readerId();
        long outsider = readers.register(new Accounts.Credentials("future-outsider", "test-pass42")).readerId();
        libraries.save(owner.shopId(), new Libraries.Settings(false, 480, 1200, 60));
        var ownSeat = libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("F1", "阅读区", false, true, true));
        var sharedSeat = libraries.saveSeat(owner.shopId(), null, new Libraries.SeatInput("F2", "阅读区", false, true, true));
        libraries.save(owner.shopId(), new Libraries.Settings(true, 480, 1200, 60));
        reservations.create(a, "future-social", request(ownSeat.id(), "2032-06-02T09:00:00+08:00"));
        var bookingB = reservations.create(b, "future-social", request(sharedSeat.id(), "2032-06-02T09:00:00+08:00"));
        var bookingC = reservations.create(c, "future-social", request(sharedSeat.id(), "2032-06-03T09:00:00+08:00"));
        assertEquals("RESERVATION_REQUIRED", assertThrows(ApiException.class, () -> social.present(outsider, "future-social")).code());
        var targets = social.present(a, "future-social").stream().filter(seat -> seat.id() == sharedSeat.id()).toList();
        assertEquals(2, targets.size());
        assertTrue(targets.stream().allMatch(ReaderSocialSeat -> ReaderSocialSeat.available() && ReaderSocialSeat.chatEnabled() && !ReaderSocialSeat.remindersEnabled()));
        assertEquals(2, targets.stream().map(cn.shopbooking.social.ReaderSocial.PresentSeat::startsAt).distinct().count());
        var targetB = new cn.shopbooking.social.ReaderSocial.Target(sharedSeat.id(), "VOICE", bookingB.id());
        var targetC = new cn.shopbooking.social.ReaderSocial.Target(sharedSeat.id(), "VOICE", bookingC.id());
        assertEquals("RESERVATION_NOT_STARTED", assertThrows(ApiException.class, () -> social.remind(a, "future-social", targetC)).code());
        social.preferences(b, new cn.shopbooking.social.ReaderSocial.Preferences("稍后聊", false));
        assertEquals("CHAT_DISABLED", assertThrows(ApiException.class, () -> social.invite(a, "future-social", targetB)).code());
        var chatC = social.invite(a, "future-social", targetC);
        assertEquals("PENDING", social.chats(a).getFirst().status());
        assertTrue(social.chats(b).isEmpty());
        assertEquals(chatC.id(), social.chats(c).getFirst().id());
        var message = new cn.shopbooking.social.ReaderSocial.Send("明天一起学习吗？", UUID.randomUUID());
        assertThrows(ApiException.class, () -> social.send(a, chatC.id(), message));
        social.action(c, chatC.id(), "accept");
        social.send(a, chatC.id(), message);
        assertEquals(1, social.messages(c, chatC.id()).size());
        assertThrows(ApiException.class, () -> social.messages(b, chatC.id()));
        social.preferences(b, new cn.shopbooking.social.ReaderSocial.Preferences("稍后聊", true));
        var chatB = social.invite(a, "future-social", targetB);
        reservations.readerAction(b, bookingB.id(), "cancel");
        assertEquals("PENDING", social.chats(a).stream().filter(chat -> chat.id().equals(chatB.id())).findFirst().orElseThrow().status());
        social.action(b, chatB.id(), "accept");
        assertEquals("ACCEPTED", social.chats(a).stream().filter(chat -> chat.id().equals(chatB.id())).findFirst().orElseThrow().status());
        assertEquals("READER_CHANGED", assertThrows(ApiException.class, () -> social.invite(a, "future-social", targetB)).code());
    }

    private int tryReserve(long reader, SeatReservations.Create input) {
        return tryReserve(reader, input, "SLOT_UNAVAILABLE");
    }

    private int tryReserve(long reader, SeatReservations.Create input, String code) {
        try {
            reservations.create(reader, "seat-test", input);
            return 1;
        } catch (ApiException conflict) {
            assertEquals(code, conflict.code());
            return 0;
        }
    }
}
