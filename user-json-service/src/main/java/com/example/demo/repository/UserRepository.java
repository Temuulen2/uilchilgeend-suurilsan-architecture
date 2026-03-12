package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.model.UserProfile;

public interface UserRepository extends JpaRepository<UserProfile, Long>{

}