package com.mohammadmurrar.leadflow.service.api;

import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {
    private final ServiceOfferingService service;

    public ServiceController(ServiceOfferingService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ServiceResponse> findAll(@RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.findAll(search, active, pageable);
    }

    @GetMapping("/active")
    public Page<ServiceOptionResponse> active(@RequestParam(required = false) String search,
            @PageableDefault(size = 200) Pageable pageable) {
        return service.findActiveOptions(search, pageable);
    }

    @GetMapping("/{id}")
    public ServiceResponse findOne(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/services/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public ServiceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateServiceRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public ServiceResponse deactivate(@PathVariable UUID id, @Valid @RequestBody ServiceVersionRequest request) {
        return service.deactivate(id, request.version());
    }

    @PostMapping("/{id}/reactivate")
    public ServiceResponse reactivate(@PathVariable UUID id, @Valid @RequestBody ServiceVersionRequest request) {
        return service.reactivate(id, request.version());
    }
}
