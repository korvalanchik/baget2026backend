package com.example.baget.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;


@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Ramarnya API", version = "${api.version}",
                contact = @Contact(name = "Ramarnya", email = "user@mppu.org.ua", url = "https://www.mppu.org.ua"),
                license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0"), termsOfService = "${tos.uri}",
                description = "${api.description}"),
        servers = {
                @io.swagger.v3.oas.annotations.servers.Server(url = "http://localhost:8080", description = "Development"),
                @io.swagger.v3.oas.annotations.servers.Server(url = "${api.server.url}", description = "Production")})

public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        // Створюємо компоненти для OpenAPI
        Components components = new Components()
                // Додаємо схеми для обробки помилок
                .addSchemas("ApiErrorResponse", new ObjectSchema()
                        .addProperty("status", new IntegerSchema())
                        .addProperty("code", new StringSchema())
                        .addProperty("message", new StringSchema())
                        .addProperty("fieldErrors", new ArraySchema().items(
                                new Schema<ArraySchema>().$ref("ApiFieldError"))))
                .addSchemas("ApiFieldError", new ObjectSchema()
                        .addProperty("code", new StringSchema())
                        .addProperty("message", new StringSchema())
                        .addProperty("property", new StringSchema())
                        .addProperty("rejectedValue", new ObjectSchema())
                        .addProperty("path", new StringSchema()))
                // Додаємо схему для безпеки (JWT авторизація)
                .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Provide the JWT token. JWT token can be obtained from the Login API. For testing, use the credentials <strong>john/password</strong>")
                        .bearerFormat("JWT"));

        // Створюємо OpenAPI з усіма компонентами та налаштуваннями
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(components);
    }

    @Bean
    public OperationCustomizer operationCustomizer() {
        // add error type to each operation
        return (operation, handlerMethod) -> {
            operation.getResponses().addApiResponse("4xx/5xx", new ApiResponse()
                    .description("Error")
                    .content(new Content().addMediaType("*/*", new MediaType().schema(
                            new Schema<MediaType>().$ref("ApiErrorResponse")))));
            return operation;
        };
    }

}
