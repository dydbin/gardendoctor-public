package com.project.farming.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "context-test"})
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void generatedSchemasShouldRetainActualCommonResponsePayloadTypes() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode openApi = objectMapper.readTree(document);

        assertSchemaContains(openApi, "/api/plants", "get", "PageResponse", "PlantResponse");
        assertSchemaContains(openApi, "/api/diaries/my-diaries", "get", "PageResponse", "DiaryResponse");
        assertSchemaContains(openApi, "/api/chat/sessions", "get", "PageResponse", "ChatRoomResponse");
        assertSchemaContains(openApi, "/auth/login", "post", "JwtToken");
        assertStatusSchemaContains(
                openApi, "/api/photo-analysis/analyze", "post", "201", "PhotoAnalysisSidebarResponse");

        assertStatusSchemaContains(openApi, "/api/chat/sessions", "get", "401", "CommonErrorResponse");
        assertThat(response(openApi, "/api/chat/{chatId}", "delete", "204").path("content").isMissingNode())
                .isTrue();

        assertThat(operation(openApi, "/auth/login", "post").path("security").isEmpty()).isTrue();
        assertThat(operation(openApi, "/auth/forgot-password", "post").path("security").isEmpty()).isTrue();
        assertThat(operation(openApi, "/auth/password-reset/confirm", "post").path("security").isEmpty()).isTrue();
        assertThat(operation(openApi, "/api/plants", "get").path("security").toString())
                .contains("jwtAuth");
        assertThat(openApi.path("paths").has("/auth/find-email")).isFalse();
        assertThat(operation(openApi, "/api/diaries/my-diaries", "get").path("deprecated").asBoolean())
                .isTrue();

        assertPrefixDescription(openApi, "/api/plants/search");
        assertPrefixDescription(openApi, "/api/farms/search");
    }

    private void assertSchemaContains(JsonNode openApi, String path, String method, String... typeNames) {
        assertStatusSchemaContains(openApi, path, method, "200", typeNames);
    }

    private void assertStatusSchemaContains(
            JsonNode openApi, String path, String method, String statusCode, String... typeNames) {
        JsonNode content = response(openApi, path, method, statusCode).path("content");
        JsonNode schema = content.isObject() && content.elements().hasNext()
                ? content.elements().next().path("schema")
                : content.path("schema");

        assertThat(schema.isMissingNode()).as("Missing response schema for %s %s", method, path).isFalse();
        assertThat(schema.toString()).contains(typeNames);
    }

    private void assertPrefixDescription(JsonNode openApi, String path) {
        String description = operation(openApi, path, "get").path("description").asText();
        assertThat(description).contains("시작").doesNotContain("포함");
    }

    private JsonNode response(JsonNode openApi, String path, String method, String statusCode) {
        return operation(openApi, path, method).path("responses").path(statusCode);
    }

    private JsonNode operation(JsonNode openApi, String path, String method) {
        return openApi.path("paths").path(path).path(method);
    }
}
