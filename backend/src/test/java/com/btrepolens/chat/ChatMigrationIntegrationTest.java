package com.btrepolens.chat;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MVP_TEST_DB_URL", matches = ".+")
class ChatMigrationIntegrationTest {
    @Test void upgradingV3PreservesConversationAndProposal() throws Exception {
        String url = System.getenv("MVP_TEST_DB_URL");
        String username = System.getenv("MVP_TEST_DB_USERNAME");
        String password = System.getenv().getOrDefault("MVP_TEST_DB_PASSWORD", "");
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT current_database()")) {
                assertTrue(result.next());
                if (!result.getString(1).endsWith("_test")) {
                    throw new IllegalStateException("Migration tests require a dedicated database whose name ends in _test");
                }
            }

            var configuration = Flyway.configure().dataSource(url, username, password)
                .schemas(schema).defaultSchema(schema).cleanDisabled(false);
            try {
                configuration.target("3").load().migrate();
                connection.setSchema(schema);
                statement.executeUpdate("""
                    INSERT INTO tracked_repository(id,owner,name,full_name,github_url,active,created_at,updated_at)
                    VALUES (1,'test','repo','test/repo','https://github.com/test/repo',true,now(),now())
                    """);
                statement.executeUpdate("INSERT INTO chat_session(id,repository_id,title) VALUES (1,1,'Existing conversation')");
                statement.executeUpdate("INSERT INTO chat_message(session_id,role,content) VALUES (1,'USER','Existing message')");
                statement.executeUpdate("INSERT INTO issue_proposal(session_id,repository_id,title,body) VALUES (1,1,'Existing proposal','Criteria')");

                Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema).load().migrate();

                try (var result = statement.executeQuery("""
                    SELECT s.title, s.turn_generation, m.content, p.title, p.state
                    FROM chat_session s JOIN chat_message m ON m.session_id=s.id
                    JOIN issue_proposal p ON p.session_id=s.id WHERE s.id=1
                    """)) {
                    assertTrue(result.next());
                    assertEquals("Existing conversation", result.getString(1));
                    assertEquals(0L, result.getLong(2));
                    assertEquals("Existing message", result.getString(3));
                    assertEquals("Existing proposal", result.getString(4));
                    assertEquals("PENDING", result.getString(5));
                }
            } finally {
                configuration.load().clean();
            }
        }
    }
}
