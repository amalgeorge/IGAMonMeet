package com.igamonmeet.dashboard;

import com.igamonmeet.dashboard.DashboardService.Dashboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/meetings/{id}/dashboard")
    public Dashboard dashboard(@PathVariable long id) {
        return service.build(id);
    }
}
