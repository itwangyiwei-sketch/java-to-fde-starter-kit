package dev.javatofde.starter.api;

import dev.javatofde.starter.flow.ReconcileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReconcileController {
    private final ReconcileService service;

    public ReconcileController(ReconcileService service) {
        this.service = service;
    }

    @PostMapping("/reconcile")
    public ReconcileResponse reconcile(@Valid @RequestBody ReconcileRequest request) {
        return service.reconcile(request);
    }
}
