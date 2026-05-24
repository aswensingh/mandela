package com.marketinghub.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DbInfoController {

    private final JdbcTemplate jdbcTemplate;

    public DbInfoController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/db-info")
    public DbInfoResponse dbInfo() {
        String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
        return new DbInfoResponse("postgres", version);
    }

    public record DbInfoResponse(String db, String version) {}
}
