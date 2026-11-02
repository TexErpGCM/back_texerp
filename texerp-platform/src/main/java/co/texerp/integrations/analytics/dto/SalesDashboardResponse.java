
package co.texerp.integrations.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesDashboardResponse(
        LocalDate from,
        LocalDate to,
        boolean hasData,
        Metrics summary,
        Comparison comparison,
        List<Ranking> topProducts,
        List<Ranking> topCustomers
) {

    public record Metrics(
            BigDecimal totalSales,
            BigDecimal unitsSold,
            long salesCount,
            BigDecimal averageTicket,
            BigDecimal totalCost,
            BigDecimal profit,
            BigDecimal marginPercentage,
            boolean valuationComplete,
            long missingCostCount
    ) {}

    public record Comparison(
            LocalDate previousFrom,
            LocalDate previousTo,
            Metrics previous,
            Change sales,
            Change profit,
            Change units
    ) {}

    public record Change(
            BigDecimal absolute,
            BigDecimal percentage
    ) {}

    public record Ranking(
            Long id,
            String name,
            BigDecimal sales,
            BigDecimal cost,
            BigDecimal profit,
            BigDecimal marginPercentage,
            BigDecimal units,
            boolean valuationComplete,
            long missingCostCount
    ) {}
}
