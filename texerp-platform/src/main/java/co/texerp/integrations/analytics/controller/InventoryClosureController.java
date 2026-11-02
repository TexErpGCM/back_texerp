
package co.texerp.integrations.analytics.controller;

import co.texerp.integrations.analytics.dto.InventoryClosureResult;
import co.texerp.integrations.analytics.service.InventoryClosureService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics/inventory")
@RequiredArgsConstructor
public class InventoryClosureController {

    private final InventoryClosureService service;

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/close")
    public InventoryClosureResult close(
            Authentication authentication
    ) {
        return service.closeToday(authentication.getName());
    }
}
