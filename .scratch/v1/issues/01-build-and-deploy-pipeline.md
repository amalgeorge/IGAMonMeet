# 01 Build, deploy and CI pipeline

Blocked by: none

Make the skeleton build and deploy end to end.

- `mvn verify` builds the React app into the WAR and runs tests.
- WAR deploys to a local Tomcat 9 with OpenJDK 17; `/api/health` returns `{"status":"UP"}` over TLS on the Tomcat connector.
- GitHub Actions: `mvn verify`, plus a job that runs all Flyway migrations against an MSSQL service container.
- Dependency vulnerability scan in CI (ADR 0001).
