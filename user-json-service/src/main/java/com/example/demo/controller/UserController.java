package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.example.demo.model.UserProfile;
import com.example.demo.repository.UserRepository;

import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    UserRepository repo;

    @PostMapping
    public UserProfile create(@RequestBody UserProfile user){
        return repo.save(user);
    }

    @GetMapping("/{id}")
    public Optional<UserProfile> get(@PathVariable Long id){
        return repo.findById(id);
    }

    @PutMapping("/{id}")
    public UserProfile update(@PathVariable Long id, @RequestBody UserProfile user){

        user.setId(id);
        return repo.save(user);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id){

        repo.deleteById(id);
        return "deleted";
    }
}