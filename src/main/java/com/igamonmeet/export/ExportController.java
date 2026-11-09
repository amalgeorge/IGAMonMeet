package com.igamonmeet.export;

import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private static final MediaType PPTX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private final ExportService service;
    private final DocumentExport document;

    public ExportController(ExportService service, DocumentExport document) {
        this.service = service;
        this.document = document;
    }

    /** Any signed-in User may export. */
    @PostMapping("/api/meetings/{id}/export/pptx")
    public ResponseEntity<byte[]> pptx(@PathVariable long id, @RequestBody ExportService.Request r) throws IOException {
        ExportService.File f = service.pptx(id, r);
        return ResponseEntity.ok().contentType(PPTX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.name()).build().toString())
                .body(f.content());
    }

    /** The Meeting as document-shaped JSON for conversion to Word (docs/json-export.md). Any signed-in User may export. */
    @PostMapping("/api/meetings/{id}/export/json")
    public ResponseEntity<byte[]> json(@PathVariable long id, @RequestBody(required = false) ExportService.Request r, Authentication auth) {
        ExportService.File f = document.json(id, r, auth.getName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.name()).build().toString())
                .body(f.content());
    }
}
