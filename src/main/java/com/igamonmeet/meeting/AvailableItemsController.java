package com.igamonmeet.meeting;

import com.igamonmeet.meeting.AvailableItems.Catalogue;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AvailableItemsController {

    private final AvailableItems items;

    public AvailableItemsController(AvailableItems items) {
        this.items = items;
    }

    @GetMapping("/api/meetings/{id}/available")
    public Catalogue catalogue(@PathVariable long id, Authentication auth) {
        return items.catalogue(id, MeetingController.isAdmin(auth));
    }

    @PostMapping("/api/meetings/{id}/available/sections/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sectionOn(@PathVariable long id, @PathVariable String key, Authentication auth) {
        items.sectionOn(id, key, auth.getName(), MeetingController.isAdmin(auth));
    }

    @DeleteMapping("/api/meetings/{id}/available/sections/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sectionOff(@PathVariable long id, @PathVariable String key, Authentication auth) {
        items.sectionOff(id, key, auth.getName(), MeetingController.isAdmin(auth));
    }

    @PostMapping("/api/meetings/{id}/available/sections/{key}/kpis/{kpi}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kpiOn(@PathVariable long id, @PathVariable String key, @PathVariable String kpi, Authentication auth) {
        items.kpiOn(id, key, kpi, auth.getName(), MeetingController.isAdmin(auth));
    }

    @DeleteMapping("/api/meetings/{id}/available/sections/{key}/kpis/{kpi}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kpiOff(@PathVariable long id, @PathVariable String key, @PathVariable String kpi, Authentication auth) {
        items.kpiOff(id, key, kpi, auth.getName(), MeetingController.isAdmin(auth));
    }
}
