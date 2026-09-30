package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.DbConnection;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface DbConnectionRepository extends CrudRepository<DbConnection, String> {

    Optional<DbConnection> findByName(String name);

    @Query(value = "SELECT * FROM db_connections WHERE name = :name", nativeQuery = true)
    Optional<DbConnection> queryByName(String name);

    @Query(value = "SELECT password FROM db_connections WHERE name = :name", nativeQuery = true)
    Optional<String> findRawPasswordByName(String name);

    @Query(value = "SELECT COUNT(*) FROM db_connections WHERE name = :name", nativeQuery = true)
    long countByName(String name);

    default boolean existsByName(String name) {
        return countByName(name) > 0;
    }

    @Query(value = "SELECT * FROM db_connections WHERE active = true", nativeQuery = true)
    List<DbConnection> findByActiveTrue();
}