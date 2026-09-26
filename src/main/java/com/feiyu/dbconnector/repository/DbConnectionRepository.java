package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.DbConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DbConnectionRepository extends JpaRepository<DbConnection, String> {

    Optional<DbConnection> findByName(String name);
}
