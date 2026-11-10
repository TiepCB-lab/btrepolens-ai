package com.btrepolens.approval;

import com.btrepolens.common.AppException;
import com.btrepolens.github.GitHubClient;
import com.btrepolens.github.GitHubModels.IssueView;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named="MVP_TEST_DB_URL",matches=".+")
class ProposalIntegrationTest {
    static { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv("MVP_TEST_DB_URL"));
        r.add("spring.datasource.username",()->System.getenv("MVP_TEST_DB_USERNAME"));
        r.add("spring.datasource.password",()->System.getenv().getOrDefault("MVP_TEST_DB_PASSWORD",""));
    }
    @Autowired JdbcTemplate db;
    @Autowired ProposalService proposals;
    @MockitoBean GitHubClient github;
    long repo,session,proposal;
    @BeforeEach void setup(){
        String database=db.queryForObject("SELECT current_database()",String.class);
        if(database == null || !database.endsWith("_test")) throw new IllegalStateException("Integration tests require a dedicated database whose name ends in _test");
        db.update("UPDATE tracked_repository SET active=false WHERE active=true");
        String name="integration-"+java.util.UUID.randomUUID();
        repo=db.queryForObject("INSERT INTO tracked_repository(owner,name,full_name,github_url,active,created_at,updated_at) VALUES ('test',?,?,?,true,now(),now()) RETURNING id",Long.class,name,"test/"+name,"https://github.com/test/"+name);
        session=db.queryForObject("INSERT INTO chat_session(repository_id,title) VALUES (?,?) RETURNING id",Long.class,repo,"test");
        proposal=proposals.create(session,repo,"Title","Acceptance criteria: test",List.of()).id();
    }
    @AfterEach void clean(){db.update("DELETE FROM issue_proposal WHERE session_id=?",session);db.update("DELETE FROM chat_session WHERE id=?",session);db.update("DELETE FROM tracked_repository WHERE id=?",repo);}
    @Test void concurrentApprovalsSendOnlyOneWrite() throws Exception {
        when(github.createApprovedIssue(anyString(),anyString(),anyString(),anyString(),anyList())).thenAnswer(i->{Thread.sleep(200);return new IssueView(1,"Title","open",null,"https://github.com/test/repo/issues/1",null,null,List.of());});
        try(var pool=Executors.newFixedThreadPool(2)){
            var latch=new CountDownLatch(1);
            Callable<String> task=()->{latch.await();try{return proposals.approve(proposal).state();}catch(AppException e){return e.code();}};
            var a=pool.submit(task);var b=pool.submit(task);latch.countDown();
            assertEquals(java.util.Set.of("SUCCEEDED","PROPOSAL_NOT_AVAILABLE"),java.util.Set.of(a.get(),b.get()));
        }
        verify(github,times(1)).createApprovedIssue(anyString(),anyString(),anyString(),anyString(),anyList());
    }
    @Test void uncertainWriteIsNeverRetried(){
        when(github.createApprovedIssue(anyString(),anyString(),anyString(),anyString(),anyList())).thenThrow(new RuntimeException("timeout"));
        assertEquals("APPROVAL_OUTCOME_UNKNOWN",assertThrows(AppException.class,()->proposals.approve(proposal)).code());
        assertEquals("UNKNOWN",proposals.get(proposal).state());
        assertThrows(AppException.class,()->proposals.approve(proposal));
        verify(github,times(1)).createApprovedIssue(anyString(),anyString(),anyString(),anyString(),anyList());
    }
    @Test void inactiveRepositoryCannotExecuteProposal(){
        db.update("UPDATE tracked_repository SET active=false WHERE id=?",repo);
        assertEquals("PROPOSAL_NOT_AVAILABLE",assertThrows(AppException.class,()->proposals.approve(proposal)).code());
        assertEquals("PENDING",proposals.get(proposal).state());
        verifyNoInteractions(github);
    }
    @Test void rejectedProposalCannotBeExecuted(){
        assertEquals("REJECTED",proposals.reject(proposal).state());
        assertThrows(AppException.class,()->proposals.approve(proposal));
        verifyNoInteractions(github);
    }
}
