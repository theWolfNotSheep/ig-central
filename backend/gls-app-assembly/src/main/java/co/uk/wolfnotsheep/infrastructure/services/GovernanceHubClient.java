package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for connecting to the Governance Hub — browses and downloads
 * governance packs for import into this IG Central instance.
 */
@Service
public class GovernanceHubClient {

    private static final Logger log = LoggerFactory.getLogger(GovernanceHubClient.class);

    private final AppConfigService configService;
    private final HttpClient httpClient;
    private final String envHubUrl;
    private final String envApiKey;

    public GovernanceHubClient(
            AppConfigService configService,
            @Value("${governance-hub.url:}") String envHubUrl,
            @Value("${governance-hub.api-key:}") String envApiKey) {
        this.configService = configService;
        this.envHubUrl = envHubUrl;
        this.envApiKey = envApiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String getHubUrl() {
        String url = configService.getValue("governance.hub.url", envHubUrl);
        if (url != null && url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private String getApiKey() {
        return configService.getValue("governance.hub.api_key", envApiKey);
    }

    public boolean isConfigured() {
        String url = getHubUrl();
        String key = getApiKey();
        return url != null && !url.isBlank() && key != null && !key.isBlank();
    }

    /**
     * Make a GET request to the hub API.
     */
    public String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(getHubUrl() + path))
                .header("X-Hub-Api-Key", getApiKey())
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("Hub API error " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    /**
     * Make a POST request to the hub API.
     */
    public String post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(getHubUrl() + path))
                .header("X-Hub-Api-Key", getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("Hub API error " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }
}
