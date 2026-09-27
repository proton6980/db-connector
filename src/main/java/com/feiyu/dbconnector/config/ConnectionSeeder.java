package com.feiyu.dbconnector.config;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** 启动时把 YAML 明文种子连接加密入库；连接配置管理界面是 Phase 2，P1 靠这个入库。 */
@Component
public class ConnectionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConnectionSeeder.class);

    private final List<ConnectionSeedProperties.Spec> seeds;
    private final DbConnectionRepository repository;

    public ConnectionSeeder(ConnectionSeedProperties props, DbConnectionRepository repository) {
        this.seeds = props.connections() == null ? List.of() : props.connections();
        this.repository = repository;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        for (ConnectionSeedProperties.Spec spec : seeds) {
            if (repository.findByName(spec.name()).isPresent()) {
                continue;
            }
            DbConnection c = new DbConnection();
            c.setName(spec.name());
            c.setDbType(spec.dbType());
            c.setHost(spec.host());
            c.setPort(spec.port());
            c.setUsername(spec.username());
            c.setPassword(spec.password()); // converter 落库时自动加密
            c.setDatabaseName(spec.databaseName());
            c.setExtraParams(spec.extraParams());
            repository.save(c);
            log.info("种子连接已入库: {}", spec.name());
        }
    }
}
