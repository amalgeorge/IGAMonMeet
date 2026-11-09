package com.igamonmeet.version;

import com.igamonmeet.version.VersionService.Change;
import com.igamonmeet.version.VersionService.RestoreResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {

    public record Row(int versionNo, String reason, String label, String createdBy, Instant createdAt) {
    }

    public record SaveRequest(String label) {
    }

    private final VersionService service;

    public VersionController(VersionService service) {
        this.service = service;
    }

    @GetMapping("/api/meetings/{id}/versions")
    public List<Row> list(@PathVariable long id) {
        return service.list(id).stream().map(v -> new Row(v.getVersionNo(), v.getReason(), v.getLabel(), v.getCreatedBy(), v.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @PostMapping("/api/meetings/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> save(@PathVariable long id, @RequestBody(required = false) SaveRequest r, Authentication auth) {
        return Map.of("versionNo", service.saveManual(id, r == null ? null : r.label(), auth.getName(), isAdmin(auth)).getVersionNo());
    }

    /** {@code to} omitted compares against the Meeting as it is now. */
    @GetMapping("/api/meetings/{id}/versions/diff")
    public List<Change> diff(@PathVariable long id, @RequestParam int from, @RequestParam(required = false) Integer to) {
        return service.diff(id, from, to);
    }

    @PostMapping("/api/meetings/{id}/versions/{versionNo}/restore")
    public RestoreResult restore(@PathVariable long id, @PathVariable int versionNo, Authentication auth) {
        return service.restore(id, versionNo, auth.getName(), isAdmin(auth));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
