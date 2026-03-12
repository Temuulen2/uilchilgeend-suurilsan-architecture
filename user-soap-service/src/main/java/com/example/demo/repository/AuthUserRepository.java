package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.model.AuthUser;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long>{

    AuthUser findByUsername(String username);

}