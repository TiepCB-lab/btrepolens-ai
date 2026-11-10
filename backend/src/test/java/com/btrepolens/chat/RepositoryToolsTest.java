package com.btrepolens.chat;

import com.btrepolens.approval.ProposalService;
import com.btrepolens.common.AppException;
import com.btrepolens.github.GitHubClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositoryToolsTest {
    @Test void issueLookupUsesOnlyTheServerBoundRepository() {
        var chats = mock(ChatService.class);
        var github = mock(GitHubClient.class);
        var db = mock(JdbcTemplate.class);
        var tools = new RepositoryTools(1, 42, "trusted-owner", "trusted-repo", chats, github, mock(ProposalService.class), db);
        tools.listIssues();
        verify(chats).requireActive(42);
        verify(github).listIssues("trusted-owner", "trusted-repo", "open", 20);
    }
    @Test void inactiveScopePreventsAnyGithubCall() {
        var chats = mock(ChatService.class);
        var github = mock(GitHubClient.class);
        var db = mock(JdbcTemplate.class);
        doThrow(new AppException(HttpStatus.CONFLICT, "REPOSITORY_NOT_ACTIVE", "inactive")).when(chats).requireActive(42);
        var tools = new RepositoryTools(1, 42, "owner", "repo", chats, github, mock(ProposalService.class), db);
        assertThrows(AppException.class, tools::listIssues);
        verifyNoInteractions(github);
        verify(db).update("INSERT INTO tool_execution_log(session_id,tool_name,success) VALUES (?,?,?)", 1L, "listIssues", false);
    }
    @Test void rejectsCallsBeyondBudget() {
        var chats = mock(ChatService.class);
        var github = mock(GitHubClient.class);
        var db = mock(JdbcTemplate.class);
        when(github.listIssues("owner", "repo", "open", 20)).thenReturn(List.of());
        var tools = new RepositoryTools(1, 42, "owner", "repo", chats, github, mock(ProposalService.class), db);
        for (int i=0; i<12; i++) tools.listIssues();
        assertFalse(tools.canCall());
        assertThrows(IllegalStateException.class, tools::listIssues);
        verify(github, times(12)).listIssues("owner", "repo", "open", 20);
    }
}
