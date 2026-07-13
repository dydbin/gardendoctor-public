package com.project.farming.global.config;

import com.project.farming.global.response.CommonErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "jwtAuth";
        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
        ModelConverters.getInstance().read(CommonErrorResponse.class)
                .forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("Farming API 문서")
                        .description("텃밭 작물 관리 서비스의 REST API 명세서입니다.")
                        .version("1.0.0"))
                .components(components);
    }

    @Bean
    public OperationCustomizer responseContractCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getResponses() == null) {
                return operation;
            }
            operation.getResponses().forEach((statusCode, response) -> {
                if ("204".equals(statusCode)) {
                    response.setContent(null);
                } else if (!statusCode.startsWith("2")) {
                    response.setContent(commonErrorContent());
                }
            });
            return operation;
        };
    }

    private Content commonErrorContent() {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/CommonErrorResponse");
        return new Content().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(schema));
    }
}
