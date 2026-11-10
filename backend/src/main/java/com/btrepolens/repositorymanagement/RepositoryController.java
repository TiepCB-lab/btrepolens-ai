package com.btrepolens.repositorymanagement;
import com.btrepolens.common.ApiResponse;
import com.btrepolens.github.GitHubModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/repositories")
@Validated
public class RepositoryController {
    private final RepositoryService repositories; private final RepositoryReadService reads;
    public RepositoryController(RepositoryService repositories, RepositoryReadService reads){this.repositories=repositories;this.reads=reads;}
    public record RegisterRequest(@NotBlank @Size(max=100) @Pattern(regexp="[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?") String owner,
                                  @NotBlank @Size(max=100) @Pattern(regexp="(?!\\.{1,2}$)[A-Za-z0-9_.-]+") String name) {}
    @GetMapping public ApiResponse<List<RepositoryService.View>> list(){return ApiResponse.ok(repositories.list(),"Repositories retrieved");}
    @PostMapping public ResponseEntity<ApiResponse<RepositoryService.View>> register(@Valid @RequestBody RegisterRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(repositories.register(request.owner(),request.name()),"Repository registered"));
    }
    @PutMapping("/{id}/activate") public ApiResponse<RepositoryService.View> activate(@PathVariable @Positive long id){return ApiResponse.ok(repositories.activate(id),"Repository activated");}
    @GetMapping("/{id}/overview") public ApiResponse<Overview> overview(@PathVariable @Positive long id){
        return ApiResponse.ok(reads.overview(id),"Overview retrieved");
    }
    @GetMapping("/{id}/issues") public ApiResponse<List<IssueView>> issues(@PathVariable @Positive long id, @RequestParam(defaultValue="open") @Pattern(regexp="open|closed|all") String state, @RequestParam(defaultValue="20") @Min(1) @Max(100) int limit){
        return ApiResponse.ok(reads.issues(id,state,limit),"Issues retrieved");
    }
    @GetMapping("/{id}/issues/{number}") public ApiResponse<IssueView> issue(@PathVariable @Positive long id,@PathVariable @Positive long number){return ApiResponse.ok(reads.issue(id,number),"Issue retrieved");}
    @GetMapping("/{id}/pull-requests") public ApiResponse<List<PullRequestView>> pulls(@PathVariable @Positive long id, @RequestParam(defaultValue="open") @Pattern(regexp="open|closed|all") String state, @RequestParam(defaultValue="20") @Min(1) @Max(100) int limit){
        return ApiResponse.ok(reads.pulls(id,state,limit),"Pull requests retrieved");
    }
    @GetMapping("/{id}/pull-requests/{number}") public ApiResponse<PullRequestView> pull(@PathVariable @Positive long id,@PathVariable @Positive long number){return ApiResponse.ok(reads.pull(id,number),"Pull request retrieved");}
}
