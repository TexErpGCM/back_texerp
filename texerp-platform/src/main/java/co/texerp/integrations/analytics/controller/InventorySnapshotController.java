
package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.dto.InventorySnapshotResult;
import co.texerp.integrations.analytics.service.InventorySnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/inventory")
@RequiredArgsConstructor
public class InventorySnapshotController {

    private final InventorySnapshotService service;

    @PostMapping("/snapshot")
    public InventorySnapshotResult capture() {
        return service.captureToday();
    }
}
