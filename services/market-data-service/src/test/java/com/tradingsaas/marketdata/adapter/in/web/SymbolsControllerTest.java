package com.tradingsaas.marketdata.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.adapter.in.web.dto.PagedResponse;
import com.tradingsaas.marketdata.adapter.in.web.dto.SymbolResponse;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.port.in.GetSymbolsUseCase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SymbolsControllerTest {

    private final GetSymbolsUseCase useCase = mock(GetSymbolsUseCase.class);
    private final SymbolsController controller = new SymbolsController(useCase);

    @Test
    void returnsPagedSymbolsWithDefaultPagination() {
        Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ", "Technology", true);
        Page<Symbol> page = new PageImpl<>(List.of(symbol), PageRequest.of(0, 20), 1);
        when(useCase.getSymbols(any())).thenReturn(page);

        ResponseEntity<PagedResponse<SymbolResponse>> response = controller.getSymbols(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().content().size());
        assertEquals("AAPL", response.getBody().content().getFirst().ticker());
        assertEquals(0, response.getBody().page());
        assertEquals(1, response.getBody().totalElements());
    }

    @Test
    void clampsPageSizeToMax() {
        Page<Symbol> empty = Page.empty();
        when(useCase.getSymbols(any())).thenReturn(empty);

        ResponseEntity<PagedResponse<SymbolResponse>> response = controller.getSymbols(0, 999);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void returnsEmptyPageWhenNoSymbols() {
        when(useCase.getSymbols(any())).thenReturn(Page.empty());

        ResponseEntity<PagedResponse<SymbolResponse>> response = controller.getSymbols(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().totalElements());
    }
}
