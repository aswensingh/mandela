package com.marketinghub.auth;

public class UsernameAlreadyUsedException extends RuntimeException {
    public UsernameAlreadyUsedException(String username) {
        super("Username already taken: " + username);
    }
}
