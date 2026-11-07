package com.igamonmeet.template;

import com.igamonmeet.template.TemplateDtos.Draft;
import com.igamonmeet.template.TemplateDtos.Version;
import com.igamonmeet.template.TemplateDtos.VersionSummary;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemplateController {

    private final TemplateService templates;
    private final TemplateVersionRepository versions;

    public TemplateController(TemplateService templates, TemplateVersionRepository versions) {
        this.templates = templates;
        this.versions = versions;
    }

    @GetMapping("/api/templates/current")
    @Transactional(readOnly = true)
    public Version current() {
        return TemplateDtos.of(templates.current());
    }

    @GetMapping("/api/templates/{versionNo}")
    @Transactional(readOnly = true)
    public Version version(@PathVariable int versionNo) {
        return TemplateDtos.of(templates.version(versionNo));
    }

    @GetMapping("/api/admin/templates")
    @Transactional(readOnly = true)
    public List<VersionSummary> list() {
        return versions.findAllByOrderByVersionNoDesc().stream()
                .map(v -> new VersionSummary(v.getVersionNo(), v.getCreatedBy(), v.getCreatedAt(), v.getNote(), v.getSections().size()))
                .collect(Collectors.toList());
    }

    @PostMapping("/api/admin/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Version publish(@Valid @RequestBody Draft draft, Authentication auth) {
        return TemplateDtos.of(templates.publish(draft, auth.getName()));
    }
}
