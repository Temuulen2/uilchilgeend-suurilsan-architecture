package com.example.demo.service;

import java.util.UUID;
import java.util.HashMap;

import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private HashMap<String,String> tokens = new HashMap<>();

    public String generateToken(String username){
        String token = UUID.randomUUID().toString();
        tokens.put(token, username);
        return token;
    }

    public boolean validateToken(String token){
        return tokens.containsKey(token);
    }
}