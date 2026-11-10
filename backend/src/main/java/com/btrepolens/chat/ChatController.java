package com.btrepolens.chat;
import com.btrepolens.approval.ProposalService;
import com.btrepolens.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/chat/sessions") @Validated
public class ChatController {
    private final ChatService chats;
    public ChatController(ChatService chats){this.chats=chats;}
    public record CreateSession(@Positive long repositoryId,@NotBlank @Size(max=160) String title) {}
    public record SendMessage(@NotBlank @Size(max=10000) String content) {}
    @GetMapping public ApiResponse<List<ChatService.Session>> list(){return ApiResponse.ok(chats.list(),"Sessions retrieved");}
    @PostMapping public ResponseEntity<ApiResponse<ChatService.Session>> create(@Valid @RequestBody CreateSession request){return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(chats.create(request.repositoryId(),request.title()),"Session created"));}
    @GetMapping("/{id}") public ApiResponse<ChatService.Session> get(@PathVariable @Positive long id){return ApiResponse.ok(chats.get(id),"Session retrieved");}
    @GetMapping("/{id}/messages") public ApiResponse<List<ChatService.ChatMessage>> messages(@PathVariable @Positive long id){return ApiResponse.ok(chats.messages(id),"Messages retrieved");}
    @PostMapping("/{id}/messages") public ApiResponse<ChatService.ChatMessage> send(@PathVariable @Positive long id,@Valid @RequestBody SendMessage request){return ApiResponse.ok(chats.send(id,request.content()),"Response generated");}
    @GetMapping("/{id}/proposals") public ApiResponse<List<ProposalService.Proposal>> proposals(@PathVariable @Positive long id){return ApiResponse.ok(chats.proposals(id),"Proposals retrieved");}
    @GetMapping("/{id}/tool-logs") public ApiResponse<List<ChatService.ToolLog>> logs(@PathVariable @Positive long id){return ApiResponse.ok(chats.logs(id),"Tool logs retrieved");}
}
