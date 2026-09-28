package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.service.ConnectionService;
import com.feiyu.dbconnector.web.connection.ConnectionForm;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
@ConditionalOnWebApplication
public class ConnectionApi {

    private final ConnectionService connectionService;

    public ConnectionApi(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<ConnectionResponse> list() {
        return connectionService.listAll().stream().map(ConnectionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ConnectionResponse get(@PathVariable String id) {
        return ConnectionResponse.from(connectionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody ConnectionForm form) {
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码不能为空"));
        }
        DbConnection created = connectionService.create(form);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConnectionResponse.from(created));
    }

    @PutMapping("/{id}")
    public ConnectionResponse update(@PathVariable String id, @Valid @RequestBody ConnectionForm form) {
        return ConnectionResponse.from(connectionService.update(id, form));
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable String id) {
        ConnectionService.TestResult result = connectionService.test(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", result.ok());
        body.put("durationMs", result.durationMs());
        body.put("message", result.message());
        return body;
    }

    @PostMapping("/{id}/reload")
    public Map<String, Object> reload(@PathVariable String id) {
        connectionService.reload(id);
        return Map.of("ok", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        connectionService.delete(id);
        return Map.of("ok", true);
    }
}
