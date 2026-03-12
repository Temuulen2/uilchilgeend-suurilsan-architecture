package com.example.demo.service;

import org.springframework.stereotype.Service;

@Service
public class SoapAuthClient {

    public boolean validateToken(String token){

        if(token == null){
            return false;
        }

        return true;
    }
}