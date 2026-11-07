package com.igamonmeet.slides;

import com.igamonmeet.slides.PlanModel.Plan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlidePlanController {

    private final SlidePlanService service;

    public SlidePlanController(SlidePlanService service) {
        this.service = service;
    }

    /** The Slide plan every view renders: Slides view, show, Presenter view and export (ADR 0004). */
    @GetMapping("/api/meetings/{id}/slides")
    public Plan slides(@PathVariable long id) {
        return service.plan(id);
    }
}
