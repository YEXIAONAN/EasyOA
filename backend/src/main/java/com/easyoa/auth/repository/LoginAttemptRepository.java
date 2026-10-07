package com.easyoa.auth.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

import com.easyoa.auth.domain.LoginAttempt;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    long countByUsernameIgnoreCaseAndSuccessfulIsFalseAndCreatedAtAfter(String username, Instant after);

    long countByIpAddressAndSuccessfulIsFalseAndCreatedAtAfter(String ipAddress, Instant after);
}