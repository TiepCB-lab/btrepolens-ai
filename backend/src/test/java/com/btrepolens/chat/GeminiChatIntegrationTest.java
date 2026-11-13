package com.btrepolens.chat;

import com.btrepolens.github.GitHubClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("gemini")
@EnabledIfEnvironmentVariable(named = "MVP_TEST_DB_URL", matches = ".+")
class GeminiChatIntegrationTest {
    private static final List<String> requests = new CopyOnWriteArrayList<>();
    private static HttpServer server;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/openai/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = requests.size() == 1 ? """
                {"id":"first","object":"chat.completion","created":1,"model":"gemini-3.5-flash-lite",
                 "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                   "tool_calls":[{"id":"call-1","type":"function","function":{"name":"listIssues","arguments":"{}"},
                     "extra_content":{"google":{"thought_signature":"test-signature"}}}]}}]}
                """ : """
                {"id":"second","object":"chat.completion","created":1,"model":"gemini-3.5-flash-lite",
                 "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"No open issues."}}]}
                """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        registry.add("spring.datasource.url", () -> System.getenv("MVP_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("MVP_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("MVP_TEST_DB_PASSWORD", ""));
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/openai");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    @AfterAll static void stopServer() { if (server != null) server.stop(0); }
    @Autowired JdbcTemplate db;
    @Autowired ChatService chats;
    @MockitoBean GitHubClient github;

    @Test void geminiProfileCallsToolsAndPreservesThoughtSignature() {
        String database = db.queryForObject("SELECT current_database()", String.class);
        if (database == null || !database.endsWith("_test")) throw new IllegalStateException("A dedicated test database is required");
        db.update("UPDATE tracked_repository SET active=false WHERE active=true");
        String name = "gemini-" + UUID.randomUUID();
        long repo = db.queryForObject("INSERT INTO tracked_repository(owner,name,full_name,github_url,active,created_at,updated_at) VALUES ('test',?,?,?,true,now(),now()) RETURNING id", Long.class, name, "test/" + name, "https://github.com/test/" + name);
        long session = chats.create(repo, "Gemini test").id();
        try {
            when(github.listIssues("test", name, "open", 20)).thenReturn(List.of());
            assertEquals("No open issues.", chats.send(session, "List issues").content());
            assertEquals(2, requests.size());
            assertTrue(requests.get(0).contains("gemini-3.5-flash-lite"));
            assertTrue(requests.get(1).contains("test-signature"));
            verify(github).listIssues("test", name, "open", 20);
        } finally {
            db.update("DELETE FROM tool_execution_log WHERE session_id=?", session);
            db.update("DELETE FROM chat_message WHERE session_id=?", session);
            db.update("DELETE FROM chat_session WHERE id=?", session);
            db.update("DELETE FROM tracked_repository WHERE id=?", repo);
        }
    }
}
