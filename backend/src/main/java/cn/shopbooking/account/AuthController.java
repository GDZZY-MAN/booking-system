package cn.shopbooking.account;

import cn.shopbooking.account.Accounts.Viewer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record Token(String token, String headerName) {}
    private final Accounts accounts;
    private final Readers readers;
    private final PlatformAdmins admins;
    private final HttpSessionSecurityContextRepository repository;

    public AuthController(Accounts accounts, Readers readers, PlatformAdmins admins, HttpSessionSecurityContextRepository repository) {
        this.accounts = accounts;
        this.readers = readers;
        this.admins = admins;
        this.repository = repository;
    }

    @GetMapping("/csrf")
    public Token csrf(CsrfToken token) {
        return new Token(token.getToken(), token.getHeaderName());
    }

    @GetMapping("/me")
    public Viewer me(@AuthenticationPrincipal Viewer viewer) { return viewer; }

    @PostMapping("/register")
    public Viewer register(@RequestBody Accounts.Register input, HttpServletRequest request, HttpServletResponse response) {
        return signIn(accounts.register(input), request, response);
    }

    @PostMapping("/login")
    public Viewer login(@RequestBody Accounts.Credentials input, HttpServletRequest request, HttpServletResponse response) {
        return signIn(accounts.login(input), request, response);
    }

    @PostMapping("/accept")
    public Viewer accept(@RequestBody Accounts.Accept input, HttpServletRequest request, HttpServletResponse response) {
        return signIn(accounts.accept(input), request, response);
    }

    @PostMapping("/reader/register")
    public Viewer readerRegister(@RequestBody Accounts.Credentials input, HttpServletRequest request,
                                 HttpServletResponse response) {
        return signIn(readers.register(input), request, response);
    }

    @PostMapping("/reader/login")
    public Viewer readerLogin(@RequestBody Accounts.Credentials input, HttpServletRequest request,
                              HttpServletResponse response) {
        return signIn(readers.login(input), request, response);
    }

    @PostMapping("/admin/login")
    public Viewer adminLogin(@RequestBody Accounts.Credentials input, HttpServletRequest request, HttpServletResponse response) {
        return signIn(admins.login(input), request, response);
    }

    private Viewer signIn(Viewer viewer, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(viewer, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + viewer.role()))));
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
        return viewer;
    }
}
