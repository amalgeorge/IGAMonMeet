# Tomcat 9 and Spring Boot 2.7, accepting end of open-source support

The app must run like the existing IdentityIQ setup: OpenJDK, Tomcat 9 and MSSQL. Tomcat 9 implements `javax.servlet`, so the newest Spring we can use is Spring Boot 2.7 / Spring Framework 5.3 / Spring Security 5.8. Free (open-source) support for these lines has ended, so new CVEs are only fixed under paid extended support. We accepted this to stay on the standard platform; the app is packaged as a WAR.

## Consequences

- Moving to Tomcat 10.1 later means Spring Boot 3, the `jakarta.*` namespace and a migration of every servlet, filter and JPA import.
- Dependency scanning must run in CI so known CVEs in the 2.7 line are visible.
