package com.example.demo.endpoint;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.*;

import com.example.demo.model.AuthUser;
import com.example.demo.repository.AuthUserRepository;
import com.example.demo.service.TokenService;

@Endpoint
public class AuthEndpoint {

    @Autowired
    AuthUserRepository repo;

    @Autowired
    TokenService tokenService;

    @PayloadRoot(namespace="http://auth", localPart="RegisterUserRequest")
    @ResponsePayload
    public String register(@RequestPayload String username, String password){

        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setPassword(password);

        repo.save(user);

        return "User registered";
    }

    @PayloadRoot(namespace="http://auth", localPart="LoginUserRequest")
    @ResponsePayload
    public String login(@RequestPayload String username, String password){

        AuthUser user = repo.findByUsername(username);

        if(user != null && user.getPassword().equals(password)){

            return tokenService.generateToken(username);
        }

        return "Invalid credentials";
    }

}