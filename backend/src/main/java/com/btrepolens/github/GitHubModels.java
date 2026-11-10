package com.btrepolens.github;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
public final class GitHubModels {
    private GitHubModels() {}
    public record Owner(String login) {}
    public record Repository(String name, Owner owner, String description, @JsonProperty("private") boolean privateRepository,
        String visibility, @JsonProperty("default_branch") String defaultBranch, @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("updated_at") Instant updatedAt) {}
    public record Label(String name) {}
    public record Issue(long number, String title, String state, String body, @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt,
        List<Label> labels, @JsonProperty("pull_request") Object pullRequest) {}
    public record PullRequest(long number, String title, String state, String body, @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt) {}
    public record SearchCount(@JsonProperty("total_count") long totalCount, @JsonProperty("incomplete_results") boolean incompleteResults) {}
    public record IssueView(long number, String title, String state, String body, String url, Instant createdAt, Instant updatedAt, List<String> labels) {
        public static IssueView from(Issue i) { return new IssueView(i.number(), i.title(), i.state(), i.body(), i.htmlUrl(), i.createdAt(), i.updatedAt(), i.labels() == null ? List.of() : i.labels().stream().map(Label::name).toList()); }
    }
    public record PullRequestView(long number, String title, String state, String body, String url, Instant createdAt, Instant updatedAt) {
        public static PullRequestView from(PullRequest p) { return new PullRequestView(p.number(), p.title(), p.state(), p.body(), p.htmlUrl(), p.createdAt(), p.updatedAt()); }
    }
    public record RepositoryView(String name, String owner, String description, String visibility, String defaultBranch, String url, Instant updatedAt) {
        public static RepositoryView from(Repository r) { return new RepositoryView(r.name(), r.owner().login(), r.description(), r.visibility() == null ? (r.privateRepository() ? "private" : "public") : r.visibility(), r.defaultBranch(), r.htmlUrl(), r.updatedAt()); }
    }
    public record Count(long total, boolean incomplete) {}
    public record IssueSummary(long number, String title, String state, String url, Instant updatedAt, List<String> labels) {
        public static IssueSummary from(IssueView i) { return new IssueSummary(i.number(), i.title(), i.state(), i.url(), i.updatedAt(), i.labels()); }
    }
    public record PullRequestSummary(long number, String title, String state, String url, Instant updatedAt) {
        public static PullRequestSummary from(PullRequestView p) { return new PullRequestSummary(p.number(), p.title(), p.state(), p.url(), p.updatedAt()); }
    }
    public record Overview(RepositoryView repository, Count openIssues, Count openPullRequests, List<IssueSummary> recentIssues, List<PullRequestSummary> recentPullRequests) {}
}
