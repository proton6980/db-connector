package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.DbConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DbConnectionRepository extends JpaRepository<DbConnection, String> {

    Optional<DbConnection> findByName(String name);

    List<DbConnection> findByActiveTrue();

    /** 绕过 converter 直接取落库密文（仅测试用）。 */
    @Query(value = "SELECT password FROM db_connections WHERE name = :name", nativeQuery = true)
    String findRawPasswordByName(@Param("name") String name);
}
