package com.btrepolens.approval;
import com.btrepolens.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/proposals") @Validated
public class ProposalController {
    private final ProposalService proposals;
    public ProposalController(ProposalService proposals){this.proposals=proposals;}
    public record Confirm(@Pattern(regexp="APPROVE") String confirmation) {}
    @GetMapping("/{id}") public ApiResponse<ProposalService.Proposal> get(@PathVariable @Positive long id){return ApiResponse.ok(proposals.get(id),"Proposal retrieved");}
    @PostMapping(value="/{id}/approve", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ProposalService.Proposal> approve(@PathVariable @Positive long id, @Valid @RequestBody Confirm confirm){
        if (!"APPROVE".equals(confirm.confirmation())) throw new com.btrepolens.common.AppException(org.springframework.http.HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Explicit confirmation is required");
        return ApiResponse.ok(proposals.approve(id),"Proposal executed");
    }
    @PostMapping(value="/{id}/reject", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ProposalService.Proposal> reject(@PathVariable @Positive long id, @RequestBody java.util.Map<String,Object> ignored){return ApiResponse.ok(proposals.reject(id),"Proposal rejected");}
}
