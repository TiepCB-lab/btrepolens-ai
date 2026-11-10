package com.btrepolens.repositorymanagement;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="tracked_repository")
public class TrackedRepository {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=100) private String owner;
    @Column(nullable=false, length=100) private String name;
    @Column(name="full_name", nullable=false, length=201) private String fullName;
    @Column(name="github_url", nullable=false, length=512) private String githubUrl;
    @Column(nullable=false) private boolean active;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    protected TrackedRepository() {}
    public TrackedRepository(String owner, String name, String url) { this.owner=owner; this.name=name; this.fullName=owner+"/"+name; this.githubUrl=url; this.createdAt=Instant.now(); this.updatedAt=createdAt; }
    public Long getId(){return id;} public String getOwner(){return owner;} public String getName(){return name;}
    public String getFullName(){return fullName;} public String getGithubUrl(){return githubUrl;} public boolean isActive(){return active;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public void setActive(boolean active){this.active=active; this.updatedAt=Instant.now();}
}
