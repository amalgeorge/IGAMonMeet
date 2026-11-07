package com.igamonmeet.house;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class HouseTemplateController {

    private static final MediaType PPTX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private final HouseTemplateService service;
    private final HouseTemplateRepository repository;

    public HouseTemplateController(HouseTemplateService service, HouseTemplateRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    /** Theme for in-app slides: colours, fonts, per-layout background and placeholder boxes (% of slide). */
    @GetMapping(value = "/api/house-template/theme", produces = MediaType.APPLICATION_JSON_VALUE)
    public String theme() {
        return service.currentTheme();
    }

    @GetMapping("/api/admin/house-templates")
    public Map<String, Object> list() {
        List<Object> versions = new ArrayList<>(repository.summaries());
        versions.add(Map.of("versionNo", 0, "fileName", "IAM_Monthly_Meeting_Template.pptx (bundled default)"));
        return Map.of("current", service.currentVersion(), "versions", versions);
    }

    @PostMapping("/api/admin/house-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> upload(@RequestParam("file") MultipartFile file, Authentication auth) throws java.io.IOException {
        return Map.of("versionNo", service.upload(file.getOriginalFilename(), file.getBytes(), auth.getName()));
    }

    @GetMapping("/api/admin/house-templates/{versionNo}/file")
    public ResponseEntity<byte[]> download(@PathVariable int versionNo) {
        return ResponseEntity.ok()
                .contentType(PPTX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("IAM_House_Template_v" + versionNo + ".pptx").build().toString())
                .body(service.file(versionNo));
    }
}
