package com.igamonmeet.audit;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.persistence.criteria.Predicate;
import javax.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (secured under /api/admin/**). */
@RestController
public class AuditController {

    private static final DateTimeFormatter SGT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Singapore"));
    private static final int CSV_LIMIT = 100_000;

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/admin/audit")
    public Map<String, Object> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                    @RequestParam(required = false) String actor,
                                    @RequestParam(required = false) String action,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        Page<AuditEntry> result = repository.findAll(filter(from, to, actor, action),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), newestFirst()));
        return Map.of("items", result.getContent(), "total", result.getTotalElements(), "page", result.getNumber());
    }

    @GetMapping("/api/admin/audit.csv")
    public void csv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                    @RequestParam(required = false) String actor,
                    @RequestParam(required = false) String action,
                    HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"audit-log.csv\"");
        PrintWriter out = response.getWriter();
        out.write('﻿'); // BOM so Excel reads UTF-8
        out.println("time_utc,time_sgt,actor,action,target_type,target_id,field,old_value,new_value,detail");
        for (AuditEntry e : repository.findAll(filter(from, to, actor, action), PageRequest.of(0, CSV_LIMIT, newestFirst()))) {
            out.println(String.join(",", cell(e.getOccurredAt().toString()), cell(SGT.format(e.getOccurredAt())),
                    cell(e.getActor()), cell(e.getAction()), cell(e.getTargetType()), cell(e.getTargetId()),
                    cell(e.getField()), cell(e.getOldValue()), cell(e.getNewValue()), cell(e.getDetail())));
        }
    }

    /** Quotes a CSV cell and neutralises spreadsheet formulas (CSV injection). */
    static String cell(String v) {
        if (v == null) {
            return "";
        }
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static Sort newestFirst() {
        return Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
    }

    private static Specification<AuditEntry> filter(Instant from, Instant to, String actor, String action) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
            if (to != null) p.add(cb.lessThan(root.<Instant>get("occurredAt"), to));
            if (actor != null && !actor.isBlank()) p.add(cb.equal(cb.upper(root.<String>get("actor")), actor.trim().toUpperCase()));
            if (action != null && !action.isBlank()) p.add(cb.equal(root.<String>get("action"), action.trim()));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
