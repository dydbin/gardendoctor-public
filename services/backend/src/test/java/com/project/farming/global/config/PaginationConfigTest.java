package com.project.farming.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationConfigTest {

    @Test
    void pageableResolverShouldClampRequestedSizeToOneHundred() throws Exception {
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
        new PaginationConfig().pageableCustomizer().customize(resolver);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("page", "2");
        request.addParameter("size", "1000");
        Method method = PaginationConfigTest.class.getDeclaredMethod("pageableParameter", Pageable.class);

        Pageable pageable = (Pageable) resolver.resolveArgument(
                new MethodParameter(method, 0),
                null,
                new ServletWebRequest(request),
                null);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @SuppressWarnings("unused")
    private void pageableParameter(Pageable pageable) {
    }
}
