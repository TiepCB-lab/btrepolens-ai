package com.btrepolens.github;
import com.btrepolens.common.AppException;
import com.btrepolens.github.GitHubModels.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.net.http.HttpClient;

@Component
public class GitHubClient {
    private final RestClient client;
    public GitHubClient(@Value("${github.base-url}") String baseUrl, @Value("${github.token:}") String token,
                        @Value("${github.connect-timeout:5s}") Duration connect, @Value("${github.read-timeout:15s}") Duration read) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(connect).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(read);
        var builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader(HttpHeaders.USER_AGENT, "BTRepoLens-AI");
        if (!token.isBlank()) builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        this.client = builder.build();
    }
    private <T> T get(String path, Map<String, ?> params, ParameterizedTypeReference<T> type) {
        try {
            return client.get().uri(b -> { var u = b.path(path); params.forEach(u::queryParam); return u.build(); }).retrieve()
                .onStatus(s -> s.is3xxRedirection(), (request, response) -> { throw new AppException(HttpStatus.BAD_GATEWAY, "GITHUB_REDIRECT", "GitHub resource moved; verify its identity before registering again"); })
                .body(type);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean limited = status == 429 || (status == 403 && e.getResponseHeaders() != null &&
                ("0".equals(e.getResponseHeaders().getFirst("X-RateLimit-Remaining")) || e.getResponseHeaders().getFirst("Retry-After") != null));
            if (limited) throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "GITHUB_RATE_LIMIT", "GitHub rate limit exceeded; retry later");
            if (status == 401) throw new AppException(HttpStatus.BAD_GATEWAY, "GITHUB_UNAUTHORIZED", "GitHub credentials are invalid");
            if (status == 403) throw new AppException(HttpStatus.FORBIDDEN, "GITHUB_FORBIDDEN", "GitHub access is forbidden");
            if (status == 404) throw new AppException(HttpStatus.NOT_FOUND, "GITHUB_NOT_FOUND", "GitHub resource not found or inaccessible");
            throw new AppException(HttpStatus.BAD_GATEWAY, "GITHUB_ERROR", "GitHub service request failed");
        } catch (ResourceAccessException e) {
            throw new AppException(HttpStatus.GATEWAY_TIMEOUT, "GITHUB_UNAVAILABLE", "GitHub could not be reached or timed out");
        } catch (RestClientException e) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "GITHUB_ERROR", "GitHub returned an invalid response");
        }
    }
    private static final ParameterizedTypeReference<Repository> REPO = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Issue> ISSUE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Issue>> ISSUES = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<PullRequest> PR = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<PullRequest>> PRS = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<SearchCount> COUNT = new ParameterizedTypeReference<>() {};
    private String repo(String owner, String name) { return "/repos/" + owner + "/" + name; }
    public Repository getRepository(String owner, String name) { return get(repo(owner,name), Map.of(), REPO); }
    public List<IssueView> listIssues(String owner, String name, String state, int limit) {
        List<IssueView> result = new ArrayList<>();
        for (int page = 1; page <= 10 && result.size() < limit; page++) {
            List<Issue> batch = get(repo(owner,name) + "/issues", Map.of("state", state, "per_page", 100, "page", page, "sort", "updated", "direction", "desc"), ISSUES);
            for (Issue issue : batch) if (issue.pullRequest() == null && result.size() < limit) result.add(IssueView.from(issue));
            if (batch.size() < 100) break;
        }
        return result;
    }
    public IssueView getIssue(String owner, String name, long number) {
        Issue i = get(repo(owner,name) + "/issues/" + number, Map.of(), ISSUE);
        if (i.pullRequest() != null) throw new AppException(HttpStatus.NOT_FOUND, "GITHUB_NOT_FOUND", "Issue not found");
        return IssueView.from(i);
    }
    public List<PullRequestView> listPullRequests(String owner, String name, String state, int limit) {
        List<PullRequest> list = get(repo(owner,name) + "/pulls", Map.of("state", state, "per_page", limit, "sort", "updated", "direction", "desc"), PRS);
        return list.stream().map(PullRequestView::from).toList();
    }
    public PullRequestView getPullRequest(String owner, String name, long number) { return PullRequestView.from(get(repo(owner,name) + "/pulls/" + number, Map.of(), PR)); }
    public Count count(String owner, String name, String type) {
        SearchCount c = get("/search/issues", Map.of("q", "repo:" + owner + "/" + name + " is:open is:" + type, "per_page", 1), COUNT);
        return new Count(c.totalCount(), c.incompleteResults());
    }
}
