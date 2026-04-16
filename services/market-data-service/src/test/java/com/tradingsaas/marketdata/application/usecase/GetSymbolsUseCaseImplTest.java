package com.tradingsaas.marketdata.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.port.out.SymbolRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class GetSymbolsUseCaseImplTest {

    private final SymbolRepository repository = mock(SymbolRepository.class);
    private final GetSymbolsUseCaseImpl useCase = new GetSymbolsUseCaseImpl(repository);

    @Test
    void delegatesPageableToRepository() {
        Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Symbol> expected = new PageImpl<>(List.of(symbol), pageable, 1);

        when(repository.findAll(pageable)).thenReturn(expected);

        Page<Symbol> result = useCase.getSymbols(pageable);

        assertEquals(expected, result);
        verify(repository).findAll(pageable);
    }
}
