package com.btrepolens.approval;

import com.btrepolens.common.AppException;
import com.btrepolens.chat.ChatTurnFence;
import com.btrepolens.github.GitHubClient;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalService {
    public record Proposal(long id, long sessionId, long repositoryId, String title, String body, List<String> labels,
                           String state, String githubUrl, Instant createdAt, Instant updatedAt) {}
    private final JdbcTemplate db;
    private final GitHubClient github;
    private final ProposalClaim claim;
    public ProposalService(JdbcTemplate db, GitHubClient github, ProposalClaim claim) { this.db=db; this.github=github; this.claim=claim; }

    private static Proposal map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        String labels = rs.getString("labels");
        return new Proposal(rs.getLong("id"), rs.getLong("session_id"), rs.getLong("repository_id"), rs.getString("title"),
            rs.getString("body"), labels.isBlank() ? List.of() : Arrays.asList(labels.split("\\n")), rs.getString("state"),
            rs.getString("github_url"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
    public Proposal get(long id) {
        return db.query("SELECT * FROM issue_proposal WHERE id=?", ProposalService::map, id).stream().findFirst()
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,"PROPOSAL_NOT_FOUND","Proposal not found"));
    }
    public List<Proposal> list(long sessionId) { return db.query("SELECT * FROM issue_proposal WHERE session_id=? ORDER BY id DESC", ProposalService::map, sessionId); }
    public Proposal createForTurn(long sessionId, long repositoryId, long generation, int pid, String title, String body, List<String> labels) {
        if (title == null || title.isBlank() || title.length() > 256 || body == null || body.isBlank() || body.length() > 20000 || labels == null || labels.size() > 10 ||
            labels.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 100 || s.contains("\n") || s.contains("\r")))
            throw new AppException(HttpStatus.BAD_REQUEST,"INVALID_PROPOSAL","Proposal content is invalid");
        var ids = db.query("""
            INSERT INTO issue_proposal(session_id,repository_id,title,body,labels)
            SELECT s.id,s.repository_id,?,?,? FROM chat_session s
            WHERE s.id=? AND s.repository_id=? AND s.turn_generation=? AND %s
            FOR UPDATE OF s RETURNING id
            """.formatted(ChatTurnFence.HELD_LOCK), (r,n)->r.getLong(1), title.trim(), body, String.join("\n", labels), sessionId, repositoryId, generation, pid);
        if (ids.isEmpty()) throw ChatTurnFence.superseded();
        return get(ids.getFirst());
    }
    public Proposal reject(long id) {
        int updated=db.update("UPDATE issue_proposal SET state='REJECTED',updated_at=now() WHERE id=? AND state='PENDING'",id);
        if (updated == 0) { get(id); throw new AppException(HttpStatus.CONFLICT,"PROPOSAL_NOT_PENDING","Proposal is no longer pending"); }
        return get(id);
    }
    public Proposal approve(long id) {
        Proposal proposal=claim.claim(id);
        try {
            var repo=db.queryForMap("SELECT owner,name FROM tracked_repository WHERE id=?",proposal.repositoryId());
            var issue=github.createApprovedIssue((String)repo.get("owner"),(String)repo.get("name"),proposal.title(),proposal.body(),proposal.labels());
            db.update("UPDATE issue_proposal SET state='SUCCEEDED',github_url=?,updated_at=now() WHERE id=? AND state='EXECUTING'",issue.url(),id);
        } catch (RuntimeException e) {
            db.update("UPDATE issue_proposal SET state='UNKNOWN',updated_at=now() WHERE id=? AND state='EXECUTING'",id);
            throw new AppException(HttpStatus.BAD_GATEWAY,"APPROVAL_OUTCOME_UNKNOWN","Issue creation could not be confirmed. Check GitHub before taking any further action.");
        }
        return get(id);
    }
}

@Service
class ProposalClaim {
    private final JdbcTemplate db;
    private final ProposalService proposals;
    ProposalClaim(JdbcTemplate db, @org.springframework.context.annotation.Lazy ProposalService proposals) { this.db=db; this.proposals=proposals; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ProposalService.Proposal claim(long id) {
        db.queryForObject("SELECT id FROM repository_selection_lock WHERE id=1 FOR UPDATE",Integer.class);
        var proposal=proposals.get(id);
        int updated=db.update("UPDATE issue_proposal p SET state='EXECUTING',updated_at=now() WHERE p.id=? AND p.state='PENDING' AND EXISTS (SELECT 1 FROM tracked_repository r WHERE r.id=p.repository_id AND r.active=TRUE)",id);
        if(updated==0) throw new AppException(HttpStatus.CONFLICT,"PROPOSAL_NOT_AVAILABLE","Proposal must be pending and its repository active");
        return proposal;
    }
}
