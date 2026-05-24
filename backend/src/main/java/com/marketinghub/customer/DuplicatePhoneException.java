package com.marketinghub.customer;

public class DuplicatePhoneException extends RuntimeException {
    public DuplicatePhoneException(String phone) {
        super("A customer with phone " + phone + " already exists in this tenant");
    }
}
