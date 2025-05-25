package com.viewserver.mockdata.generator;

import com.viewserver.data.kafka.DataPublisher;
import com.viewserver.data.model.Account;
import com.viewserver.data.model.Instrument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generates static reference data (accounts and instruments) on demand.
 * This data serves as the foundation for all other generated data.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StaticDataGenerator {
    
    private final DataPublisher dataPublisher;
    
    /**
     * Static account data - fund strategies
     */
    private static final List<Account> ACCOUNTS = Arrays.asList(
            Account.builder()
                    .accountId("ACC001")
                    .accountName("Global Growth Fund")
                    .build(),
            Account.builder()
                    .accountId("ACC002")
                    .accountName("US Value Strategy")
                    .build(),
            Account.builder()
                    .accountId("ACC003")
                    .accountName("Emerging Markets Equity")
                    .build(),
            Account.builder()
                    .accountId("ACC004")
                    .accountName("European Small Cap")
                    .build(),
            Account.builder()
                    .accountId("ACC005")
                    .accountName("Technology Innovation Fund")
                    .build()
    );
    
    /**
     * Static instrument data - equities and currencies
     */
    private static final List<Instrument> INSTRUMENTS = Arrays.asList(
            // Technology Stocks
            Instrument.builder()
                    .instrumentId("AAPL")
                    .instrumentName("Apple Inc")
                    .instrumentType("EQUITY")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Technology")
                    .subSectors(Arrays.asList("Consumer Electronics", "Software"))
                    .build(),
            Instrument.builder()
                    .instrumentId("MSFT")
                    .instrumentName("Microsoft Corp")
                    .instrumentType("EQUITY")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Technology")
                    .subSectors(Arrays.asList("Software", "Cloud Computing"))
                    .build(),
            Instrument.builder()
                    .instrumentId("GOOGL")
                    .instrumentName("Alphabet Inc")
                    .instrumentType("EQUITY")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Technology")
                    .subSectors(Arrays.asList("Internet Services", "Advertising"))
                    .build(),
            
            // Financial Stocks
            Instrument.builder()
                    .instrumentId("JPM")
                    .instrumentName("JPMorgan Chase & Co")
                    .instrumentType("EQUITY")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Financials")
                    .subSectors(Arrays.asList("Banking", "Investment Banking"))
                    .build(),
            Instrument.builder()
                    .instrumentId("BAC")
                    .instrumentName("Bank of America Corp")
                    .instrumentType("EQUITY")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Financials")
                    .subSectors(Arrays.asList("Banking", "Retail Banking"))
                    .build(),
            
            // European Stocks
            Instrument.builder()
                    .instrumentId("NESN")
                    .instrumentName("Nestle SA")
                    .instrumentType("EQUITY")
                    .currency("CHF")
                    .countryOfRisk("CH")
                    .countryOfDomicile("CH")
                    .sector("Consumer Staples")
                    .subSectors(Arrays.asList("Food Products", "Beverages"))
                    .build(),
            Instrument.builder()
                    .instrumentId("ASML")
                    .instrumentName("ASML Holding NV")
                    .instrumentType("EQUITY")
                    .currency("EUR")
                    .countryOfRisk("NL")
                    .countryOfDomicile("NL")
                    .sector("Technology")
                    .subSectors(Arrays.asList("Semiconductors", "Manufacturing Equipment"))
                    .build(),
            
            // Healthcare Sector
            Instrument.builder()
                    .instrumentId("JNJ")
                    .instrumentName("Johnson & Johnson")
                    .instrumentType("EQUITY")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Healthcare")
                    .subSectors(Arrays.asList("Pharmaceuticals", "Medical Devices"))
                    .build(),
            
            // Bonds
            Instrument.builder()
                    .instrumentId("UST10Y")
                    .instrumentName("US Treasury 10 Year")
                    .instrumentType("BOND")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Government Bonds")
                    .subSectors(Arrays.asList("Treasury", "Fixed Income"))
                    .build(),
            Instrument.builder()
                    .instrumentId("CORP5Y")
                    .instrumentName("Corporate Bond 5 Year")
                    .instrumentType("BOND")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Corporate Bonds")
                    .subSectors(Arrays.asList("Investment Grade", "Fixed Income"))
                    .build(),
            
            // Funds
            Instrument.builder()
                    .instrumentId("SPY")
                    .instrumentName("SPDR S&P 500 ETF")
                    .instrumentType("FUND")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Equity Funds")
                    .subSectors(Arrays.asList("ETF", "Index Fund"))
                    .build(),
            
            // Cash Instruments
            Instrument.builder()
                    .instrumentId("USD")
                    .instrumentName("US Dollar Cash")
                    .instrumentType("CURRENCY")
                    .currency("USD")
                    .countryOfRisk("US")
                    .countryOfDomicile("US")
                    .sector("Cash")
                    .subSectors(Arrays.asList("Currency"))
                    .build(),
            Instrument.builder()
                    .instrumentId("EUR")
                    .instrumentName("Euro Cash")
                    .instrumentType("CURRENCY")
                    .currency("EUR")
                    .countryOfRisk("EU")
                    .countryOfDomicile("EU")
                    .sector("Cash")
                    .subSectors(Arrays.asList("Currency"))
                    .build(),
            Instrument.builder()
                    .instrumentId("GBP")
                    .instrumentName("British Pound Cash")
                    .instrumentType("CURRENCY")
                    .currency("GBP")
                    .countryOfRisk("GB")
                    .countryOfDomicile("GB")
                    .sector("Cash")
                    .subSectors(Arrays.asList("Currency"))
                    .build()
    );
    
    /**
     * Generate and publish static data manually when triggered
     */
    public void generateStaticDataNow() {
        log.info("Manually triggering static data generation...");
        generateStaticData();
    }
    
    /**
     * Internal method to generate and publish static data
     */
    private void generateStaticData() {
        log.info("Starting static data generation...");
        
        // Publish accounts
        CompletableFuture<Void> accountFutures = CompletableFuture.allOf(
                ACCOUNTS.stream()
                        .map(account -> {
                            log.debug("Publishing account: {}", account.getAccountId());
                            return dataPublisher.publishAccount(account);
                        })
                        .toArray(CompletableFuture[]::new)
        );
        
        // Publish instruments
        CompletableFuture<Void> instrumentFutures = CompletableFuture.allOf(
                INSTRUMENTS.stream()
                        .map(instrument -> {
                            log.debug("Publishing instrument: {}", instrument.getInstrumentId());
                            return dataPublisher.publishInstrument(instrument);
                        })
                        .toArray(CompletableFuture[]::new)
        );
        
        // Wait for all static data to be published
        CompletableFuture.allOf(accountFutures, instrumentFutures)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully published {} accounts and {} instruments", 
                                ACCOUNTS.size(), INSTRUMENTS.size());
                    } else {
                        log.error("Failed to publish static data: {}", ex.getMessage());
                    }
                });
    }
    
    /**
     * Get the list of account IDs for other generators
     */
    public static List<String> getAccountIds() {
        return ACCOUNTS.stream()
                .map(Account::getAccountId)
                .toList();
    }
    
    /**
     * Get the list of equity instrument IDs (non-cash) for other generators
     */
    public static List<String> getEquityInstrumentIds() {
        return INSTRUMENTS.stream()
                .filter(instrument -> !"Cash".equals(instrument.getSector()))
                .map(Instrument::getInstrumentId)
                .toList();
    }
    
    /**
     * Get the list of cash instrument IDs for other generators
     */
    public static List<String> getCashInstrumentIds() {
        return INSTRUMENTS.stream()
                .filter(instrument -> "Cash".equals(instrument.getSector()))
                .map(Instrument::getInstrumentId)
                .toList();
    }
} 