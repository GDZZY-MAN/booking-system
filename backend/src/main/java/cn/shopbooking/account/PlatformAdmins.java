package cn.shopbooking.account;

import cn.shopbooking.web.ApiException;
import cn.shopbooking.web.Inputs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdmins implements ApplicationRunner {
    private final JdbcClient db;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final String dummyHash;
    public PlatformAdmins(JdbcClient db, PasswordEncoder encoder,
                          @Value("${PLATFORM_ADMIN_USERNAME:}") String username,
                          @Value("${PLATFORM_ADMIN_PASSWORD:}") String password) {
        this.db = db; this.encoder = encoder; this.username = username; this.password = password;
        this.dummyHash = encoder.encode("admin-placeholder-password");
    }
    @Override public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) return;
        provision(username, password);
    }
    public void provision(String name, String secret) {
        String account = Accounts.username(name);
        String hash = encoder.encode(Inputs.password(secret));
        db.sql("insert into platform_admin(username, password_hash) values (?, ?) on conflict do nothing").params(account, hash).update();
    }
    public Accounts.Viewer login(Accounts.Credentials input) {
        String account = Accounts.username(input.username());
        String secret = Inputs.password(input.password());
        var hash = db.sql("select password_hash from platform_admin where username = ?").param(account).query(String.class).optional();
        if (!encoder.matches(secret, hash.orElse(dummyHash)) || hash.isEmpty()) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "管理员账号或密码有误。");
        }
        return new Accounts.Viewer(0, null, account, "ADMIN");
    }
}
