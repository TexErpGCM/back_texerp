package co.texerp.integrations.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CustomerDashboardResponse(LocalDate from, LocalDate to, boolean hasData,
                                        Summary summary, List<CustomerRanking> customers) {
    public record Summary(long purchasingCustomers, long transactions, BigDecimal totalSales,
                          BigDecimal averageTicket, long repeatCustomers,
                          BigDecimal repeatCustomerPercentage) {
    }

    public record CustomerRanking(long customerId, String name, String type, String segment,
                                  boolean active, long purchaseCount, long distinctPurchaseDays,
                                  BigDecimal sales, BigDecimal averageTicket, BigDecimal profit,
                                  boolean valuationComplete, OffsetDateTime lastPurchaseAt,
                                  boolean recurring) {
    }
}
