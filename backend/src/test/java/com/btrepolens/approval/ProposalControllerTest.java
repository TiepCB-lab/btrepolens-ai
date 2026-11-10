package com.btrepolens.approval;
import com.btrepolens.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProposalControllerTest {
    private final ProposalService service=mock(ProposalService.class);
    private MockMvc mvc;
    @BeforeEach void setup(){var validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();mvc=MockMvcBuilders.standaloneSetup(new ProposalController(service)).setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();}
    @Test void approvalRequiresExplicitConfirmation() throws Exception {
        mvc.perform(post("/api/v1/proposals/1/approve").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
}
