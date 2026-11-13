package com.btrepolens.chat;

import com.btrepolens.approval.ProposalService;
import com.btrepolens.github.GitHubClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;

public class RepositoryTools {
    private final long sessionId, repositoryId, generation;
    private final int pid;
    private final String owner,name;
    private final ChatService chats;
    private final GitHubClient github;
    private final ProposalService proposals;
    private final JdbcTemplate db;
    private final AtomicInteger calls=new AtomicInteger();
    public RepositoryTools(long sessionId,long repositoryId,long generation,int pid,String owner,String name,ChatService chats,GitHubClient github,ProposalService proposals,JdbcTemplate db){this.sessionId=sessionId;this.repositoryId=repositoryId;this.generation=generation;this.pid=pid;this.owner=owner;this.name=name;this.chats=chats;this.github=github;this.proposals=proposals;this.db=db;}
    boolean canCall(){return calls.get()<12;}
    private <T> T run(String name,Supplier<T> action){
        boolean success=false;
        try {if(calls.incrementAndGet()>12) throw new IllegalStateException("Tool call limit reached"); chats.requireCurrentTurn(sessionId,generation,pid); chats.requireActive(repositoryId); T result=action.get(); chats.requireCurrentTurn(sessionId,generation,pid); success=true; return result;}
        finally {db.update("INSERT INTO tool_execution_log(session_id,tool_name,success) VALUES (?,?,?)",sessionId,name,success);}
    }
    @Tool(description="Get metadata for the selected repository.")
    public Object getRepositoryInfo(){return run("getRepositoryInfo",()->com.btrepolens.github.GitHubModels.RepositoryView.from(github.getRepository(owner,name)));}
    @Tool(description="List up to 20 open issues in the selected repository; results may be incomplete.")
    public Object listIssues(){return run("listIssues",()->github.listIssues(owner,name,"open",20).stream().map(com.btrepolens.github.GitHubModels.IssueSummary::from).toList());}
    @Tool(description="Get one issue from the selected repository by its positive issue number.")
    public Object getIssueDetails(long number){return run("getIssueDetails",()->{if(number<1) throw new IllegalArgumentException("Invalid number");return github.getIssue(owner,name,number);});}
    @Tool(description="List up to 20 open pull requests in the selected repository.")
    public Object listPullRequests(){return run("listPullRequests",()->github.listPullRequests(owner,name,"open",20).stream().map(com.btrepolens.github.GitHubModels.PullRequestSummary::from).toList());}
    @Tool(description="Get one pull request from the selected repository by its positive number.")
    public Object getPullRequestDetails(long number){return run("getPullRequestDetails",()->{if(number<1) throw new IllegalArgumentException("Invalid number");return github.getPullRequest(owner,name,number);});}
    @Tool(description="Save a draft issue for human review. This does not create a GitHub issue. Include acceptance criteria in the body and suggest only evidence-backed labels.")
    public Object proposeIssueCreation(String title,String body,List<String> labels){return run("proposeIssueCreation",()->proposals.createForTurn(sessionId,repositoryId,generation,pid,title,body,labels));}
}
