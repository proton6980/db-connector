package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.AccountPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountPermissionRepository extends JpaRepository<AccountPermission, String> {

    List<AccountPermission> findByAccountId(String accountId);
}
