package com.btrepolens.repositorymanagement;

import com.btrepolens.common.AppException;
import com.btrepolens.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RepositoryControllerTest {
    private final RepositoryService service=mock(RepositoryService.class);
    private MockMvc mvc;
    @BeforeEach void setup(){
        var validator=new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        mvc=MockMvcBuilders.standaloneSetup(new RepositoryController(service,mock(RepositoryReadService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
    }
    @Test void rejectsInvalidRegistration() throws Exception {
        mvc.perform(post("/api/v1/repositories").contentType(MediaType.APPLICATION_JSON).content("{\"owner\":\"\",\"name\":\"..\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
    @Test void formatsApplicationErrors() throws Exception {
        when(service.activate(anyLong())).thenThrow(new AppException(HttpStatus.NOT_FOUND,"REPOSITORY_NOT_FOUND","Tracked repository not found"));
        mvc.perform(put("/api/v1/repositories/1/activate")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.errorCode").value("REPOSITORY_NOT_FOUND"));
    }
}
