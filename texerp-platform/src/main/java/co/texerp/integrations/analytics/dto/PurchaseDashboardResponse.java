package co.texerp.integrations.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseDashboardResponse(
        LocalDate from, LocalDate to, boolean hasData,
        Summary summary, List<SupplierRanking> suppliers
) {
    public record Summary(BigDecimal purchasedAmount, BigDecimal orderedQuantity,
                          BigDecimal receivedQuantity, BigDecimal pendingQuantity,
                          BigDecimal fulfillmentPercentage, BigDecimal averageDeliveryDays,
                          long purchaseOrderCount, long supplierCount) {
    }

    public record SupplierRanking(long supplierId, String supplierName,
                                  BigDecimal purchasedAmount, BigDecimal orderedQuantity,
                                  BigDecimal receivedQuantity, BigDecimal pendingQuantity,
                                  BigDecimal fulfillmentPercentage, BigDecimal averageUnitCost,
                                  BigDecimal averageDeliveryDays, long purchaseOrderCount) {
    }
}
