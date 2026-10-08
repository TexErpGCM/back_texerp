package co.texerp.integrations.controller;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.RoleDtos;
import co.texerp.integrations.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<RoleDtos.RoleResponse>> findAll() {
        return ApiResponse.ok("Roles consultados correctamente", service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleDtos.RoleResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok("Rol consultado correctamente", service.findById(id));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<RoleDtos.PermissionResponse>> permissions() {
        return ApiResponse.ok("Catálogo de permisos consultado correctamente", service.permissionCatalog());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleDtos.RoleResponse>> create(
            @Valid @RequestBody RoleDtos.CreateRoleRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Rol creado correctamente", service.create(request, http)));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleDtos.RoleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RoleDtos.UpdateRoleRequest request,
            HttpServletRequest http
    ) {
        return ApiResponse.ok("Rol actualizado correctamente", service.update(id, request, http));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            HttpServletRequest http
    ) {
        service.delete(id, http);
        return ApiResponse.ok("Rol eliminado correctamente", null);
    }
}
