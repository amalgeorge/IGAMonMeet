package com.igamonmeet.house;

import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The House template in use: the latest Admin upload, or the bundled default (version 0). */
@Service
public class HouseTemplateService {

    public static final String BUNDLED = "templates/IAM_Monthly_Meeting_Template.pptx";

    private final HouseTemplateRepository repository;
    private final TemplateInspector inspector;
    private final AuditLog audit;
    private final Clock clock;
    private volatile String bundledTheme;

    public HouseTemplateService(HouseTemplateRepository repository, TemplateInspector inspector, AuditLog audit, Clock clock) {
        this.repository = repository;
        this.inspector = inspector;
        this.audit = audit;
        this.clock = clock;
    }

    public int currentVersion() {
        Integer v = repository.latestVersion();
        return v == null ? 0 : v;
    }

    @Transactional(readOnly = true)
    public String currentTheme() {
        int v = currentVersion();
        return v == 0 ? bundledTheme() : repository.themeOf(v).orElseThrow();
    }

    @Transactional(readOnly = true)
    public byte[] file(int versionNo) {
        if (versionNo == 0) {
            return bundled();
        }
        return repository.findByVersionNo(versionNo).map(HouseTemplate::getContent)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such House template version."));
    }

    @Transactional
    public int upload(String fileName, byte[] content, String admin) {
        String name = fileName == null ? "template.pptx" : fileName.replaceAll("[\\\\/]", "_");
        String lower = name.toLowerCase();
        if (!lower.endsWith(".pptx") && !lower.endsWith(".potx")) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE", "Upload a .pptx or .potx file.");
        }
        String theme = inspector.inspect(content);
        int next = currentVersion() + 1;
        repository.save(new HouseTemplate(next, name.length() > 255 ? name.substring(0, 255) : name, content, theme, admin, clock.instant()));
        audit.record("HOUSE_TEMPLATE_UPLOADED", "HouseTemplate", next, name);
        return next;
    }

    private String bundledTheme() {
        if (bundledTheme == null) {
            bundledTheme = inspector.inspect(bundled());
        }
        return bundledTheme;
    }

    private static byte[] bundled() {
        try (InputStream in = new ClassPathResource(BUNDLED).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Bundled House template missing", e);
        }
    }
}
