package com.project.farming.global.security;

import com.project.farming.domain.farm.controller.FarmAdminController;
import com.project.farming.domain.farm.service.FarmAdminService;
import com.project.farming.domain.farm.service.FarmService;
import com.project.farming.domain.notification.controller.NoticeController;
import com.project.farming.domain.notification.service.NoticeService;
import com.project.farming.domain.plant.controller.PlantAdminController;
import com.project.farming.domain.plant.service.PlantAdminService;
import com.project.farming.domain.plant.service.PlantService;
import com.project.farming.domain.user.controller.UserAdminController;
import com.project.farming.domain.user.service.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminMutationSecurityTest {

    private PlantAdminService plantAdminService;
    private FarmAdminService farmAdminService;
    private UserAdminService userAdminService;
    private NoticeService noticeService;
    private HttpSessionCsrfTokenRepository csrfTokenRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        plantAdminService = mock(PlantAdminService.class);
        farmAdminService = mock(FarmAdminService.class);
        userAdminService = mock(UserAdminService.class);
        noticeService = mock(NoticeService.class);

        csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        CsrfFilter csrfFilter = new CsrfFilter(csrfTokenRepository);
        csrfFilter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        mockMvc = standaloneSetup(
                new PlantAdminController(plantAdminService, mock(PlantService.class)),
                new FarmAdminController(farmAdminService, mock(FarmService.class)),
                new UserAdminController(userAdminService),
                new NoticeController(noticeService)
        ).addFilters(csrfFilter).build();
    }

    @Test
    void stateChangingGetRoutesShouldBeRejected() throws Exception {
        mockMvc.perform(get("/admin/plants/delete/1")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/admin/farms/delete/2")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/admin/users/delete/3")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/admin/notices/delete/4")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/admin/notices/send/5")).andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(plantAdminService, farmAdminService, userAdminService, noticeService);
    }

    @Test
    void adminMutationWithoutCsrfTokenShouldBeRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/admin/plants/delete/1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(plantAdminService);
    }

    @Test
    void csrfProtectedPostRoutesShouldExecuteEachMutation() throws Exception {
        mockMvc.perform(post("/admin/plants/delete/1").with(validCsrfToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/plants"));
        mockMvc.perform(post("/admin/farms/delete/2").with(validCsrfToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/farms"));
        mockMvc.perform(post("/admin/users/delete/3").with(validCsrfToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
        mockMvc.perform(post("/admin/notices/delete/4").with(validCsrfToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notices"));
        mockMvc.perform(post("/admin/notices/send/5").with(validCsrfToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notices/5"));

        verify(plantAdminService).deletePlant(1L);
        verify(farmAdminService).deleteFarm(2L);
        verify(userAdminService).deleteUser(3L);
        verify(noticeService).deleteNotice(4L);
        verify(noticeService).sendNotice(5L);
    }

    private RequestPostProcessor validCsrfToken() {
        return request -> {
            CsrfToken token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, new MockHttpServletResponse());
            request.addParameter(token.getParameterName(), token.getToken());
            return request;
        };
    }
}
