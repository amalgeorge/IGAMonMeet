package com.igamonmeet.pptximport;

import com.igamonmeet.pptximport.ImportService.Mapping;
import com.igamonmeet.pptximport.ImportService.Mode;
import com.igamonmeet.pptximport.ImportService.Proposal;
import com.igamonmeet.pptximport.ImportService.Result;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImportController {

    public record ApplyRequest(@NotNull Mode mode, @NotNull List<Mapping> mappings) {
    }

    public record HistoryRow(long id, String fileName, int slideCount, String uploadedBy, Instant uploadedAt, String appliedBy,
                             Instant appliedAt, String result) {
    }

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping("/api/meetings/{id}/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public Proposal upload(@PathVariable long id, @RequestParam("file") MultipartFile file, Authentication auth) throws IOException {
        return service.upload(id, file.getOriginalFilename() == null ? "upload.pptx" : file.getOriginalFilename(), file.getBytes(),
                auth.getName(), isAdmin(auth));
    }

    @PostMapping("/api/meetings/{id}/imports/{importId}/apply")
    public Result apply(@PathVariable long id, @PathVariable long importId, @RequestBody ApplyRequest r, Authentication auth) {
        return service.apply(id, importId, r.mode(), r.mappings(), auth.getName(), isAdmin(auth));
    }

    @GetMapping("/api/meetings/{id}/imports")
    public List<HistoryRow> history(@PathVariable long id) {
        return service.history(id).stream().map(r -> new HistoryRow(r.getId(), r.getFileName(), r.getSlideCount(), r.getUploadedBy(),
                r.getUploadedAt(), r.getAppliedBy(), r.getAppliedAt(), r.getResult())).collect(Collectors.toList());
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
