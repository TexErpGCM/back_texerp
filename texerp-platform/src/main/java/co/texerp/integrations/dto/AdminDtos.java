package co.texerp.integrations.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record AuditEventResponse(
            Long id,
            Instant at,
            String username,
            String action,
            String entity,
            Long entityId,
            String details,
            String ip,
            boolean success
    ) {
    }

    public record AuditCounters(long total, long successful, long failed, long users) {
    }

    public record AuditPage(
            List<AuditEventResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record AuditManagement(
            AuditCounters counters,
            AuditPage events,
            List<String> actions,
            List<String> entities,
            Instant generatedAt
    ) {
    }

    public record UserResponse(
            Long id,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt,
            String name,
            String email,
            String role,
            boolean active
    ) {
    }

    public record UserCounters(long total, long active, long administrators, long analysts) {
    }

    public record UserPage(
            List<UserResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record UserManagement(
            UserCounters counters,
            UserPage users,
            Instant generatedAt
    ) {
    }

    public record ReportElection(
            Long id,
            String name,
            String type,
            String round,
            LocalDate electionDate,
            String state
    ) {
    }

    public record ReportCounters(
            long records,
            long votes,
            long regions,
            long reportedTables,
            long totalTables,
            double processedPercentage
    ) {
    }

    public record ReportRegion(
            String region,
            long votes,
            double participation,
            long reportedTables,
            long totalTables,
            double processedPercentage
    ) {
    }

    public record ReportGenerationResponse(
            Long id,
            String format,
            Instant generatedAt,
            String requestedBy,
            long recordCount
    ) {
    }

    public record ReportManagement(
            Long selectedElectionId,
            String selectedElectionName,
            ReportCounters counters,
            List<ReportRegion> regions,
            List<ReportElection> elections,
            Map<String, ReportGenerationResponse> lastGenerated,
            Instant generatedAt
    ) {
    }
}
