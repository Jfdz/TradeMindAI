package com.tradingsaas.marketdata.adapter.in.web;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tradingsaas.marketdata.application.usecase.ScheduledMarketDataIngestionJob;
import com.tradingsaas.marketdata.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = IngestionController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "market-data.cors.allowed-origins=http://localhost:3000",
        "market-data.internal-secret=test-secret"
})
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduledMarketDataIngestionJob ingestionJob;

    @Test
    void triggerStartsIngestionAsynchronously() throws Exception {
        mockMvc.perform(post("/api/v1/ingestion/trigger")
                        .header("X-Internal-Secret", "test-secret"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("TRIGGERED"))
                .andExpect(jsonPath("$.message").value("Ingestion started asynchronously for all tracked symbols"))
                .andExpect(jsonPath("$.triggeredAt").exists());

        verify(ingestionJob, timeout(2000)).run();
    }
}
