package cn.shopbooking.account;

import cn.shopbooking.schedule.ScheduleService;
import cn.shopbooking.shop.ShopService;
import cn.shopbooking.web.ApiException;
import cn.shopbooking.web.Inputs;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Accounts {
    public record Viewer(long shopId, Long employeeId, String username, String role, Long readerId) {
        public Viewer(long shopId, Long employeeId, String username, String role) {
            this(shopId, employeeId, username, role, null);
        }
    }
    public record Register(String username, String password, String shopName, String slug) {}
    public record Credentials(String username, String password) {}
    public record Accept(String token, String username, String password) {}
    public record Invite(String token, OffsetDateTime expiresAt) {}
    private record LoginRow(long shopId, Long employeeId, String username, String role, String passwordHash) {
        Viewer viewer() { return new Viewer(shopId, employeeId, username, role); }
    }
    private record InviteTarget(long shopId, long employeeId) {}

    private final JdbcClient db;
    private final ShopService shops;
    private final ScheduleService schedules;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public Accounts(JdbcClient db, ShopService shops, ScheduleService schedules, PasswordEncoder encoder, Clock clock) {
        this.db = db;
        this.shops = shops;
        this.schedules = schedules;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public Viewer register(Register input) {
        String username = username(input.username());
        String hash = encoder.encode(Inputs.password(input.password()));
        long shopId = shops.create(input.shopName(), input.slug());
        db.sql("insert into account(shop_id, username, password_hash, role) values (?, ?, ?, 'OWNER')")
                .params(shopId, username, hash).update();
        return new Viewer(shopId, null, username, "OWNER");
    }

    public Viewer login(Credentials input) {
        String username = username(input.username());
        String password = Inputs.password(input.password());
        var row = db.sql("""
                select shop_id, employee_id, username, role, password_hash from account where username = ?
                """).param(username).query(LoginRow.class).optional();
        if (!encoder.matches(password, row.map(LoginRow::passwordHash).orElse(dummyHash)) || row.isEmpty()) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "账号或密码有误。");
        }
        if ("OWNER".equals(row.get().role()) && !shops.isMerchant(row.get().shopId())) {
            throw new ApiException(403, "VENUE_MANAGEMENT_MOVED", "场馆管理已迁移到平台后台，请使用平台管理员账号登录。");
        }
        if (shops.isMerchant(row.get().shopId()) && !shops.isActiveMerchant(row.get().shopId())) {
            throw new ApiException(403, "MERCHANT_REMOVED", "商家已被平台删除，请联系管理员。");
        }
        return row.get().viewer();
    }

    @Transactional
    public Invite invite(long shopId, long employeeId) {
        shops.lockConfiguration(shopId);
        schedules.member(shopId, employeeId);
        boolean linked = db.sql("select exists(select 1 from account where employee_id = ?)")
                .param(employeeId).query(Boolean.class).single();
        if (linked) {
            throw ApiException.conflict("EMPLOYEE_LINKED", "这位员工已开通账号。");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime expires = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusHours(24);
        db.sql("""
                insert into employee_invite(employee_id, token_hash, expires_at) values (?, ?, ?)
                on conflict (employee_id) do update set token_hash = excluded.token_hash, expires_at = excluded.expires_at
                """).params(employeeId, digest(token), expires).update();
        return new Invite(token, expires);
    }

    @Transactional
    public Viewer accept(Accept input) {
        if (input.token() == null || !input.token().matches("[A-Za-z0-9_-]{43}")) {
            throw inviteMissing();
        }
        String username = username(input.username());
        String password = Inputs.password(input.password());
        String hash = digest(input.token());
        InviteTarget target = db.sql("""
                select e.shop_id, i.employee_id from employee_invite i
                join employee e on e.id = i.employee_id where i.token_hash = ?
                """).param(hash).query(InviteTarget.class).optional().orElseThrow(Accounts::inviteMissing);
        shops.lockConfiguration(target.shopId());
        if (!shops.isActiveMerchant(target.shopId())) throw inviteMissing();
        int deleted = db.sql("delete from employee_invite where token_hash = ? and expires_at > ?")
                .params(hash, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).update();
        if (deleted == 0) {
            throw inviteMissing();
        }
        db.sql("""
                insert into account(shop_id, employee_id, username, password_hash, role)
                values (?, ?, ?, ?, 'EMPLOYEE')
                """).params(target.shopId(), target.employeeId(), username, encoder.encode(password)).update();
        return new Viewer(target.shopId(), target.employeeId(), username, "EMPLOYEE");
    }

    private static ApiException inviteMissing() {
        return ApiException.missing("邀请已过期或使用过，请联系店主获取新链接。");
    }

    static String username(String input) {
        String username = Inputs.required(input, 80, "账号").toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9._@+-]{3,80}")) {
            throw ApiException.invalid("账号使用 3–80 位英文字母、数字或 . _ @ + -。");
        }
        return username;
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
