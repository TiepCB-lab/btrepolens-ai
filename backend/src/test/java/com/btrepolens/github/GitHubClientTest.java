package com.btrepolens.github;

import com.btrepolens.common.AppException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class GitHubClientTest {
    private HttpServer server;
    private GitHubClient client;
    private int status;
    private String response;
    private boolean rateLimited;
    private String requestPath, requestQuery;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            requestPath=exchange.getRequestURI().getPath();
            requestQuery=exchange.getRequestURI().getRawQuery();
            if(rateLimited) exchange.getResponseHeaders().add("X-RateLimit-Remaining","0");
            exchange.getResponseHeaders().add("Content-Type","application/json");
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
        server.start();
        client=new GitHubClient("http://127.0.0.1:"+server.getAddress().getPort(),"test",Duration.ofSeconds(2),Duration.ofSeconds(2));
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void mapsAndExcludesPullRequests(){
        status=200; response="""
          [{"number":1,"title":"Issue","state":"open","html_url":"https://example/1","labels":[{"name":"bug"}]},
           {"number":2,"title":"PR","state":"open","pull_request":{"url":"x"}}]
          """;
        var issues=client.listIssues("owner","repo","open",20);
        assertEquals(1,issues.size()); assertEquals("https://example/1",issues.getFirst().url()); assertEquals("bug",issues.getFirst().labels().getFirst());
    }
    @Test void rejectsPullRequestAsIssue(){status=200; response="{\"number\":2,\"pull_request\":{}}"; assertEquals("GITHUB_NOT_FOUND",assertThrows(AppException.class,()->client.getIssue("a","b",2)).code());}
    @Test void translatesAuthenticationFailure(){status=401;response="{}";assertEquals("GITHUB_UNAUTHORIZED",assertThrows(AppException.class,()->client.getRepository("a","b")).code());}
    @Test void translatesNotFound(){status=404;response="{}";assertEquals("GITHUB_NOT_FOUND",assertThrows(AppException.class,()->client.getRepository("a","b")).code());}
    @Test void translatesRateLimit(){status=403;response="{}";rateLimited=true;assertEquals("GITHUB_RATE_LIMIT",assertThrows(AppException.class,()->client.getRepository("a","b")).code());}
    @Test void rejectsEmptyResponse(){status=200;response="";assertEquals("GITHUB_ERROR",assertThrows(AppException.class,()->client.getIssue("a","b",1)).code());}
    @Test void rejectsNullListResponse(){status=200;response="null";assertEquals("GITHUB_ERROR",assertThrows(AppException.class,()->client.listPullRequests("a","b","open",10)).code());}
    @Test void returnsBoundedPullRequestFileMetadata(){
        status=200;
        response="["+java.util.stream.IntStream.range(0,31).mapToObj(i -> "{\"filename\":\"file"+i+"\",\"status\":\"modified\",\"additions\":2,\"deletions\":1,\"changes\":3,\"patch\":\"private content\"}").collect(java.util.stream.Collectors.joining(","))+"]";
        var result=client.getPullRequestFiles("owner","repo",12);
        assertEquals("/repos/owner/repo/pulls/12/files",requestPath);
        assertTrue(requestQuery.contains("per_page=31"));
        assertEquals(30,result.files().size());
        assertTrue(result.incomplete());
        assertEquals("file0",result.files().getFirst().filename());
        assertEquals(2,result.files().getFirst().additions());
        assertFalse(result.toString().contains("private content"));
    }
    @Test void reportsCompletePullRequestFileList(){status=200;response="[]";assertFalse(client.getPullRequestFiles("owner","repo",1).incomplete());}
}
