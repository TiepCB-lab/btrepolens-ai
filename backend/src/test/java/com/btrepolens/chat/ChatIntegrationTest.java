package com.btrepolens.chat;

import com.btrepolens.common.AppException;
import com.btrepolens.approval.ProposalService;
import com.btrepolens.github.GitHubClient;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "MVP_TEST_DB_URL", matches = ".+")
class ChatIntegrationTest {
    static { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("MVP_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> System.getenv("MVP_TEST_DB_USERNAME"));
        r.add("spring.datasource.password", () -> System.getenv().getOrDefault("MVP_TEST_DB_PASSWORD", ""));
    }
    @Autowired JdbcTemplate db;
    @Autowired ChatService chats;
    @Autowired ChatTurnLock lock;
    @Autowired ProposalService proposals;
    @MockitoBean ChatModel model;
    @MockitoBean GitHubClient github;
    long repositoryId, sessionId;

    @BeforeEach void setup() {
        when(model.getOptions()).thenReturn(org.springframework.ai.model.tool.ToolCallingChatOptions.builder().build());
        String database = db.queryForObject("SELECT current_database()", String.class);
        if (database == null || !database.endsWith("_test")) throw new IllegalStateException("Integration tests require a dedicated database whose name ends in _test");
        db.update("UPDATE tracked_repository SET active=false WHERE active=true");
        String name = "chat-" + UUID.randomUUID();
        repositoryId = db.queryForObject("INSERT INTO tracked_repository(owner,name,full_name,github_url,active,created_at,updated_at) VALUES ('test',?,?,?,true,now(),now()) RETURNING id", Long.class, name, "test/" + name, "https://github.com/test/" + name);
        sessionId = chats.create(repositoryId, "Test conversation").id();
    }
    @AfterEach void clean() {
        db.update("DELETE FROM issue_proposal WHERE session_id=?", sessionId);
        db.update("DELETE FROM tool_execution_log WHERE session_id=?", sessionId);
        db.update("DELETE FROM chat_message WHERE session_id=?", sessionId);
        db.update("DELETE FROM chat_session WHERE id=?", sessionId);
        db.update("DELETE FROM tracked_repository WHERE id=?", repositoryId);
    }
    private ChatResponse response() { return new ChatResponse(List.of(new Generation(new AssistantMessage("Grounded response")))); }

    @Test void connectionLossReleasesLockAndBusyState() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(response());
        try (var connection = lock.acquire(sessionId)) {
            assertTrue(chats.get(sessionId).busy());
            assertEquals("SESSION_BUSY", assertThrows(AppException.class, () -> chats.send(sessionId, "Another request")).code());
        }
        assertFalse(chats.get(sessionId).busy());
        assertEquals("Grounded response", chats.send(sessionId, "Try again").content());
        assertFalse(chats.get(sessionId).busy());
    }

    @Test void concurrentTurnsOnlySendOneRequestToModel() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(model.call(any(Prompt.class))).thenAnswer(call -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return response(); });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> chats.send(sessionId, "First"));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertTrue(chats.get(sessionId).busy());
                assertEquals("SESSION_BUSY", assertThrows(AppException.class, () -> chats.send(sessionId, "Second")).code());
            } finally { release.countDown(); }
            assertEquals("Grounded response", first.get(5, TimeUnit.SECONDS).content());
        }
        verify(model, times(1)).call(any(Prompt.class));
        assertFalse(chats.get(sessionId).busy());
        assertEquals(2, chats.messages(sessionId).size());
    }

    @Test void inactiveRepositoryCannotSendToModel() {
        db.update("UPDATE tracked_repository SET active=false WHERE id=?", repositoryId);
        assertEquals("REPOSITORY_NOT_ACTIVE", assertThrows(AppException.class, () -> chats.send(sessionId, "Hello")).code());
        verifyNoInteractions(model);
        assertTrue(chats.messages(sessionId).isEmpty());
    }

    @Test void failedModelRequestReleasesLockAndCanBeRetried() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider failure")).thenReturn(response());
        assertEquals("AI_REQUEST_FAILED", assertThrows(AppException.class, () -> chats.send(sessionId, "First")).code());
        assertFalse(chats.get(sessionId).busy());
        assertEquals("Grounded response", chats.send(sessionId, "Try again").content());
        assertFalse(chats.get(sessionId).busy());
    }

    @Test void lostLockCannotSaveAProposalEvenBeforeAnotherTurnStarts() throws Exception {
        var turn=lock.acquire(sessionId);
        try {
            assertTrue(db.queryForObject("SELECT pg_terminate_backend(?, 5000)", Boolean.class, turn.pid()));
            var error=assertThrows(AppException.class, () -> proposals.createForTurn(sessionId,repositoryId,turn.generation(),turn.pid(),"Stale","Criteria",List.of()));
            assertEquals("SESSION_SUPERSEDED",error.code());
            assertTrue(error.getMessage().contains("Your message may already be saved"));
            assertTrue(proposals.list(sessionId).isEmpty());
        } finally { assertThrows(java.sql.SQLException.class, turn::close); }
    }

    @Test void staleModelResponseCannotBeSavedAfterNewTurnStarts() throws Exception {
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var callCount=new java.util.concurrent.atomic.AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            if (callCount.incrementAndGet()==1) {
                entered.countDown();
                assertTrue(release.await(10,TimeUnit.SECONDS));
                return new ChatResponse(List.of(new Generation(new AssistantMessage("Stale response"))));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("New response"))));
        });
        try (var pool=Executors.newSingleThreadExecutor()) {
            var old=pool.submit(() -> chats.send(sessionId,"Old request"));
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS));
                Integer pid=db.queryForObject("""
                    SELECT l.pid FROM pg_locks l WHERE l.locktype='advisory' AND l.granted AND l.objsubid=2
                      AND l.database=(SELECT oid FROM pg_database WHERE datname=current_database())
                      AND l.classid::bigint=(((?::bigint >> 32) # 1112822348) & 4294967295)
                      AND l.objid::bigint=(?::bigint & 4294967295)
                    """,Integer.class,sessionId,sessionId);
                assertTrue(db.queryForObject("SELECT pg_terminate_backend(?, 5000)",Boolean.class,pid));
                assertEquals("New response",chats.send(sessionId,"New request").content());
            } finally { release.countDown(); }
            var failure=assertThrows(ExecutionException.class,() -> old.get(10,TimeUnit.SECONDS));
            assertInstanceOf(AppException.class,failure.getCause());
            assertEquals("SESSION_SUPERSEDED",((AppException)failure.getCause()).code());
        }
        var messages=chats.messages(sessionId);
        assertEquals(List.of("Old request","New request","New response"),messages.stream().map(ChatService.ChatMessage::content).toList());
    }

    @Test void activeTurnCanSaveProposal() throws Exception {
        try(var turn=lock.acquire(sessionId)) {
            var proposal=proposals.createForTurn(sessionId,repositoryId,turn.generation(),turn.pid(),"Draft","Criteria",List.of());
            assertEquals("PENDING",proposal.state());
        }
    }

    @Test void agentToolCallCreatesOnlyAPendingProposal() {
        var call=new AssistantMessage.ToolCall("call-1","function","proposeIssueCreation",
            "{\"title\":\"Draft\",\"body\":\"Acceptance criteria: test\",\"labels\":[]}");
        var withTool=AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(withTool))),response());
        assertEquals("Grounded response",chats.send(sessionId,"Draft an issue").content());
        var saved=proposals.list(sessionId);
        assertEquals(1,saved.size());
        assertEquals("PENDING",saved.getFirst().state());
        assertEquals("Draft",saved.getFirst().title());
        assertEquals("proposeIssueCreation",chats.logs(sessionId).getFirst().toolName());
        verifyNoInteractions(github);
    }
}
