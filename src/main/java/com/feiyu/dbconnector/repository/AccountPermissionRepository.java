package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.AccountPermission;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface AccountPermissionRepository extends CrudRepository<AccountPermission, Long> {
}