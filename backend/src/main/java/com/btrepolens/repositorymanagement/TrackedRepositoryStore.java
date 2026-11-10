package com.btrepolens.repositorymanagement;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TrackedRepositoryStore extends JpaRepository<TrackedRepository, Long> {
    boolean existsByFullNameIgnoreCase(String fullName);
}
