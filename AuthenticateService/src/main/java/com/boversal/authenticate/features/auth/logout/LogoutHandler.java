package com.boversal.authenticate.features.auth.logout;

import org.springframework.stereotype.Service;

@Service
public class LogoutHandler {
    public void handle() {
        // JWT is stateless; logout is completed by expiring the client cookie.
    }
}