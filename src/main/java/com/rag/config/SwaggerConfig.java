package com.rag.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RAG System API")
                        .description("Retrieval-Augmented Generation System for document search\n\n" +
                                "## Возможности:\n" +
                                "- 📄 Загрузка документов (PDF, DOCX, TXT)\n" +
                                "- 🔍 Семантический поиск по смыслу\n" +
                                "- 🏷️ Фильтрация по orderId и documentType\n" +
                                "- ⚡ Асинхронная обработка через Kafka\n" +
                                "- 🧠 Эмбеддинги через Ollama (nomic-embed-text)\n" +
                                "- 🗄️ Векторное хранение в PostgreSQL + pgvector")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RAG Team")
                                .email("support@rag-system.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://springdoc.org")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ));
    }
}