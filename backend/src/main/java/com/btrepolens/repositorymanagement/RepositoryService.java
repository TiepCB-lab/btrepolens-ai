package com.btrepolens.repositorymanagement;
import com.btrepolens.common.AppException;
import com.btrepolens.github.GitHubClient;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class RepositoryService {
    private final TrackedRepositoryStore store; private final GitHubClient github; private final EntityManager entityManager;
    public RepositoryService(TrackedRepositoryStore store, GitHubClient github, EntityManager entityManager) { this.store=store; this.github=github; this.entityManager=entityManager; }
    public record View(Long id, String owner, String name, String fullName, String githubUrl, boolean active, Instant createdAt, Instant updatedAt) {
        static View from(TrackedRepository r) {return new View(r.getId(),r.getOwner(),r.getName(),r.getFullName(),r.getGithubUrl(),r.isActive(),r.getCreatedAt(),r.getUpdatedAt());}
    }
    @Transactional(readOnly=true) public List<View> list(){return store.findAll().stream().map(View::from).toList();}
    @Transactional(readOnly=true) public TrackedRepository find(long id){return store.findById(id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,"REPOSITORY_NOT_FOUND","Tracked repository not found"));}
    @Transactional public View register(String owner, String name){
        if(store.existsByFullNameIgnoreCase(owner+"/"+name)) throw new AppException(HttpStatus.CONFLICT,"REPOSITORY_DUPLICATE","Repository already registered");
        var repo=github.getRepository(owner,name);
        if(store.existsByFullNameIgnoreCase(repo.owner().login()+"/"+repo.name())) throw new AppException(HttpStatus.CONFLICT,"REPOSITORY_DUPLICATE","Repository already registered");
        return View.from(store.saveAndFlush(new TrackedRepository(repo.owner().login(),repo.name(),repo.htmlUrl())));
    }
    @Transactional public View activate(long id){
        entityManager.createNativeQuery("SELECT id FROM repository_selection_lock WHERE id = 1 FOR UPDATE").getSingleResult();
        TrackedRepository selected=find(id);
        entityManager.createQuery("update TrackedRepository r set r.active = false, r.updatedAt = :now where r.active = true and r.id <> :id")
            .setParameter("now",Instant.now()).setParameter("id",id).executeUpdate();
        selected.setActive(true);
        return View.from(selected);
    }
}
