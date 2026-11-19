package com.saadchaib.corebank.customer;

import com.saadchaib.corebank.common.NotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    public Customer get(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer not found."));
    }

    public Page<Customer> list(Pageable pageable) {
        return customers.findAll(pageable);
    }
}
