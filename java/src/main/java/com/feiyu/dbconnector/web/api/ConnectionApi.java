package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.config.ConnectionForm;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.service.ConnectionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Controller("/api/connections")
public class ConnectionApi {

    private final ConnectionService connectionService;

    public ConnectionApi(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Get
    public List<Map<String, Object>> list() {
        return connectionService.listAll().stream().map(this::sanitize).toList();
    }

    @Get("/{id}")
    public Map<String, Object> getById(String id) {
        return sanitize(connectionService.getById(id));
    }

    @Post
    public HttpResponse<Map<String, Object>> create(@Body ConnectionForm form) {
        validateForm(form, true);
        DbConnection c = connectionService.create(form);
        return HttpResponse.created(sanitize(c));
    }

    @Put("/{id}")
    public Map<String, Object> update(String id, @Body ConnectionForm form) {
        validateForm(form, false);
        DbConnection c = connectionService.update(id, form);
        return sanitize(c);
    }

    @Delete("/{id}")
    public Map<String, Object> delete(String id) {
        connectionService.delete(id);
        return Map.of("ok", true);
    }

    @Post("/{id}/test")
    public ConnectionService.TestResult test(String id) {
        return connectionService.test(id);
    }

    @Post("/{id}/reload")
    public Map<String, Object> reload(String id) {
        connectionService.reload(id);
        return Map.of("ok", true);
    }

    private Map<String, Object> sanitize(DbConnection c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId() != null ? c.getId() : "");
        m.put("name", c.getName() != null ? c.getName() : "");
        m.put("dbType", c.getDbType() != null ? c.getDbType() : "");
        m.put("host", c.getHost() != null ? c.getHost() : "");
        m.put("port", c.getPort() != null ? c.getPort() : 0);
        m.put("username", c.getUsername() != null ? c.getUsername() : "");
        m.put("databaseName", c.getDatabaseName() != null ? c.getDatabaseName() : "");
        m.put("extraParams", c.getExtraParams() != null ? c.getExtraParams() : "");
        m.put("poolMin", c.getPoolMin() != null ? c.getPoolMin() : 2);
        m.put("poolMax", c.getPoolMax() != null ? c.getPoolMax() : 10);
        m.put("active", c.getActive() != null ? c.getActive() : true);
        m.put("allowDml", Boolean.TRUE.equals(c.getAllowDml()));
        m.put("allowDdl", Boolean.TRUE.equals(c.getAllowDdl()));
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
        m.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : "");
        return m;
    }

    private void validateForm(ConnectionForm form, boolean isNew) {
        if (form.getName() == null || form.getName().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "名称不能为空");
        }
        if (form.getDbType() == null || form.getDbType().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请选择数据库类型");
        }
        if (form.getHost() == null || form.getHost().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "主机不能为空");
        }
        if (form.getUsername() == null || form.getUsername().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "用户名不能为空");
        }
        if (isNew && (form.getPassword() == null || form.getPassword().isBlank())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "密码不能为空");
        }
    }
}