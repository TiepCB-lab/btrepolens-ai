package com.btrepolens.repositorymanagement;

import com.btrepolens.common.AppException;
import com.btrepolens.github.GitHubClient;
import com.btrepolens.github.GitHubModels.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepositoryServiceTest {
    private final TrackedRepositoryStore store=mock(TrackedRepositoryStore.class);
    private final GitHubClient github=mock(GitHubClient.class);
    private final RepositoryService service=new RepositoryService(store,github,mock(EntityManager.class));
    @Test void registersOnlyAfterVerification(){
        when(github.getRepository("owner","repo")).thenReturn(new Repository("Repo",new Owner("Owner"),null,false,"public","main","https://github.com/Owner/Repo",null));
        when(store.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        var saved=service.register("owner","repo");
        assertEquals("Owner/Repo",saved.fullName()); assertFalse(saved.active());
        verify(github).getRepository("owner","repo"); verify(store).saveAndFlush(any());
    }
    @Test void rejectsDuplicateBeforeCallingGithub(){
        when(store.existsByFullNameIgnoreCase("owner/repo")).thenReturn(true);
        assertEquals("REPOSITORY_DUPLICATE",assertThrows(AppException.class,()->service.register("owner","repo")).code());
        verifyNoInteractions(github);
    }
    @Test void doesNotSaveWhenVerificationFails(){
        when(github.getRepository("owner","repo")).thenThrow(new AppException(org.springframework.http.HttpStatus.NOT_FOUND,"GITHUB_NOT_FOUND","missing"));
        assertThrows(AppException.class,()->service.register("owner","repo")); verify(store,never()).saveAndFlush(any());
    }
}
