package dev.javatofde.starter.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfiguration {
    @Bean
    ToolCallbackProvider invoiceToolCallbacks(InvoiceTools invoiceTools) {
        return MethodToolCallbackProvider.builder().toolObjects(invoiceTools).build();
    }
}
