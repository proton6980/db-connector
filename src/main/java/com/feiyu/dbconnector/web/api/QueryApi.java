package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.service.ConnectionService;
import com.feiyu.dbconnector.service.MetadataService;
import com.feiyu.dbconnector.service.QueryService;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
@Controller("/api")
public class QueryApi {

    private final QueryService queryService;
    private final MetadataService metadataService;
    private final ConnectionService connectionService;

    public QueryApi(QueryService queryService, MetadataService metadataService, ConnectionService connectionService) {
        this.queryService = queryService;
        this.metadataService = metadataService;
        this.connectionService = connectionService;
    }

    @Post("/query")
    public QueryService.QueryResult query(@Body QueryRequest req) {
        DbConnection c = connectionService.resolve(req.connection());
        return queryService.run(c, req.sql(), req.params(), req.maxRows());
    }

    @Get("/tables")
    public Object listTables(@QueryValue String connection) throws Exception {
        DbConnection c = connectionService.resolve(connection);
        return metadataService.listTables(connectionService.readOnlyDataSource(c));
    }

    @Get("/tables/{table}/describe")
    public Object describeTable(String table, @QueryValue String connection) throws Exception {
        DbConnection c = connectionService.resolve(connection);
        return metadataService.describeTable(connectionService.readOnlyDataSource(c), table);
    }

    @Serdeable
    public record QueryRequest(String connection, String sql, Map<String, Object> params,
                               Integer maxRows) {}
}