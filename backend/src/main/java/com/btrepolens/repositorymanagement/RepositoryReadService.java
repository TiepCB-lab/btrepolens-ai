package com.btrepolens.repositorymanagement;
import com.btrepolens.github.GitHubClient;
import com.btrepolens.github.GitHubModels.*;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RepositoryReadService {
    private final RepositoryService repositories;
    private final GitHubClient github;
    public RepositoryReadService(RepositoryService repositories, GitHubClient github) { this.repositories=repositories; this.github=github; }
    public Overview overview(long id) {
        var r=repositories.find(id); String o=r.getOwner(), n=r.getName();
        return new Overview(RepositoryView.from(github.getRepository(o,n)), github.count(o,n,"issue"), github.count(o,n,"pr"), github.listIssues(o,n,"open",5).stream().map(IssueSummary::from).toList(), github.listPullRequests(o,n,"open",5).stream().map(PullRequestSummary::from).toList());
    }
    public List<IssueView> issues(long id,String state,int limit) { var r=repositories.find(id); return github.listIssues(r.getOwner(),r.getName(),state,limit); }
    public IssueView issue(long id,long number) { var r=repositories.find(id); return github.getIssue(r.getOwner(),r.getName(),number); }
    public List<PullRequestView> pulls(long id,String state,int limit) { var r=repositories.find(id); return github.listPullRequests(r.getOwner(),r.getName(),state,limit); }
    public PullRequestView pull(long id,long number) { var r=repositories.find(id); return github.getPullRequest(r.getOwner(),r.getName(),number); }
}
