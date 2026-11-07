# IGAMonMeet: IAM Monthly Meeting

Prepare, present and keep the IAM Engineering and IAM Operations monthly meeting: structured sections, PowerPoint-style slides in the browser, PPTX import and export, and a full audit trail.

- Vocabulary: [CONTEXT.md](CONTEXT.md)
- Specification: [docs/spec.md](docs/spec.md)
- Decisions: [docs/adr](docs/adr)
- Tickets: [.scratch/v1](.scratch/v1/README.md)
- Deployment: [docs/deploy.md](docs/deploy.md)
- Admin recovery and first Admin: [docs/admin-recovery.md](docs/admin-recovery.md)

## Stack

OpenJDK 17, Tomcat 9 (WAR), Spring Boot 2.7, MSSQL with Flyway, React 18 + Vite, Apache POI.

## Build

Needs JDK 17+, Maven 3.9 and Node 20+ on the build machine.

```
mvn verify                       # builds frontend into the WAR, runs tests
mvn verify -Dskip.frontend=true  # backend only
cd frontend && npm run dev       # UI dev server, proxies /api to localhost:8080
```

Output: `target/igamonmeet.war`. Database settings come from `DB_URL`, `DB_USER`, `DB_PASSWORD`.

## House template

The default PowerPoint template is `src/main/resources/templates/IAM_Monthly_Meeting_Template.pptx`. Regenerate it with `cd tools/template && npm install && npm run build`.
