package dev.javatofde.starter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelConfiguration {
    @Bean
    @ConditionalOnProperty(name = "fde.model.provider", havingValue = "mock", matchIfMissing = true)
    ProposalModel mockProposalModel() {
        return new MockProposalModel();
    }

    @Bean
    @ConditionalOnProperty(name = "fde.model.provider", havingValue = "openai")
    ProposalModel openAiProposalModel(ObjectMapper mapper,
                                      @Value("${fde.model.base-url}") String baseUrl,
                                      @Value("${fde.model.model}") String model,
                                      @Value("${fde.model.api-key}") String apiKey,
                                      @Value("${fde.model.timeout-ms}") long timeoutMs) {
        return new OpenAiCompatibleProposalModel(mapper, baseUrl, model, apiKey, timeoutMs);
    }
}
