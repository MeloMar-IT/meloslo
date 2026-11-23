package com.example.meloslo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP round trips with the actual cookie and header values, as a browser sends them.
 * Cookies are tracked by hand so the test controls exactly which ones are sent.
 */
class CsrfFunctionalTest extends BaseFunctionalTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final String SLI_BODY = "{\"apiVersion\":\"openslo/v1\",\"kind\":\"SLI\",\"name\":\"%s\","
            + "\"displayName\":\"%s\",\"spec\":\"{}\"}";

    /** A tiny cookie jar: only what the test explicitly stores. */
    private static final class Jar {
        final Map<String, String> cookies = new LinkedHashMap<>();

        String header() {
            StringBuilder sb = new StringBuilder();
            cookies.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : "; ").append(k).append('=').append(v));
            return sb.toString();
        }

        void absorb(HttpResponse<?> response) {
            for (String setCookie : response.headers().allValues("Set-Cookie")) {
                String pair = setCookie.split(";", 2)[0];
                int eq = pair.indexOf('=');
                if (eq > 0) cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }

        String token() {
            return cookies.get("XSRF-TOKEN");
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> send(HttpRequest.Builder req, Jar jar, String token) throws IOException, InterruptedException {
        if (!jar.cookies.isEmpty()) req.header("Cookie", jar.header());
        if (token != null) req.header("X-XSRF-TOKEN", token);
        HttpResponse<String> response = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        jar.absorb(response);
        return response;
    }

    private HttpResponse<String> get(String path, Jar jar) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url(path))).GET(), jar, null);
    }

    private HttpResponse<String> formLogin(Jar jar, String token, String user, String password) throws Exception {
        String form = "username=" + user + "&password=" + password;
        return send(HttpRequest.newBuilder(URI.create(url("/api/login")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)), jar, token);
    }

    private HttpResponse<String> createSli(Jar jar, String token, String name) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url("/api/v1/openslo")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(SLI_BODY.formatted(name, name))), jar, token);
    }

    @Test
    void loginMutateLogoutAndLoginAgainWorkWithTheRealTokens() throws Exception {
        Jar browser = new Jar();

        // 1. The first GET hands out the XSRF-TOKEN cookie (anonymous callers get 401 from this endpoint)
        assertThat(get("/api/v1/users/me", browser).statusCode()).isEqualTo(401);
        String token = browser.token();
        assertThat(token).isNotBlank();

        // 2. Login with the cookie and header: a new token and a session cookie
        HttpResponse<String> login = formLogin(browser, token, "admin", "admin");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(browser.cookies).containsKey("JSESSIONID");
        String loggedInToken = browser.token();
        assertThat(loggedInToken).isNotBlank();

        // 3. Session cookie without the header is refused
        Jar sessionOnly = new Jar();
        sessionOnly.cookies.put("JSESSIONID", browser.cookies.get("JSESSIONID"));
        assertThat(createSli(sessionOnly, null, "func-no-header").statusCode()).isEqualTo(403);

        // 4. Authenticated mutation with the real cookie and header succeeds
        assertThat(createSli(browser, loggedInToken, "func-with-header").statusCode()).isEqualTo(201);

        // 5. Logout with the token gives 200 and a fresh token
        HttpResponse<String> logout = send(HttpRequest.newBuilder(URI.create(url("/api/logout")))
                .POST(HttpRequest.BodyPublishers.noBody()), browser, loggedInToken);
        assertThat(logout.statusCode()).isEqualTo(200);
        String afterLogout = browser.token();
        assertThat(afterLogout).isNotBlank().isNotEqualTo(loggedInToken);

        // 6. Logging in again with the token from logout works
        Jar again = new Jar();
        again.cookies.put("XSRF-TOKEN", afterLogout);
        assertThat(formLogin(again, afterLogout, "admin", "admin").statusCode()).isEqualTo(200);
    }
}
