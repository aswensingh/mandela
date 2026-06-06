package com.marketinghub.customer;

import com.marketinghub.customer.dto.BulkDeleteCustomersRequest;
import com.marketinghub.customer.dto.BulkDeleteCustomersResponse;
import com.marketinghub.customer.dto.CreateCustomerRequest;
import com.marketinghub.customer.dto.CustomerDto;
import com.marketinghub.customer.dto.UpdateCustomerRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public Page<CustomerDto> list(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) OptInStatus optInStatus,
        @PageableDefault(size = 50) Pageable pageable
    ) {
        return customerService.list(search, tag, optInStatus, pageable);
    }

    @GetMapping("/{id}")
    public CustomerDto get(@PathVariable UUID id) {
        return customerService.get(id);
    }

    @PostMapping
    public ResponseEntity<CustomerDto> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerDto created = customerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public CustomerDto update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        customerService.delete(id);
    }

    @PostMapping("/bulk-delete")
    public BulkDeleteCustomersResponse bulkDelete(@Valid @RequestBody BulkDeleteCustomersRequest request) {
        int deleted = customerService.bulkDelete(request.ids());
        return new BulkDeleteCustomersResponse(deleted);
    }
}
