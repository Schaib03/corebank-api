package com.saadchaib.corebank.customer;

import com.saadchaib.corebank.common.PageResponse;
import com.saadchaib.corebank.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/api/me")
    @Operation(summary = "Profile of the logged-in customer")
    public CustomerResponse me(CurrentUser user) {
        return CustomerResponse.from(customerService.get(user.requireCustomerId()));
    }

    @GetMapping("/api/admin/customers")
    @Operation(summary = "List all customers (ADMIN)")
    public PageResponse<CustomerResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(customerService.list(pageable), CustomerResponse::from);
    }

    @GetMapping("/api/admin/customers/{id}")
    @Operation(summary = "Get a customer (ADMIN)")
    public CustomerResponse get(@PathVariable UUID id) {
        return CustomerResponse.from(customerService.get(id));
    }
}
