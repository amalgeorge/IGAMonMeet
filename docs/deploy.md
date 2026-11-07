# Deploying to Tomcat 9

1. Install OpenJDK 17 and Tomcat 9 on the server, plus the Arial font (or the metric-compatible Liberation Sans) for slide layout measurement (ADR 0004).
2. Create the database `igamonmeet` on the IIQ MSSQL instance and a SQL login that owns only that database (ADR 0006). Flyway creates the tables on first start.
3. Set the connection in `$CATALINA_BASE/bin/setenv.sh`:
   ```sh
   export DB_URL='jdbc:sqlserver://<host>:1433;databaseName=igamonmeet;encrypt=true'
   export DB_USER='igamonmeet'
   export DB_PASSWORD='<secret>'
   export JAVA_OPTS="$JAVA_OPTS -Duser.timezone=UTC"
   ```
4. Enable TLS on the Tomcat connector in `conf/server.xml`, and remove or firewall the plain HTTP connector:
   ```xml
   <Connector port="8443" protocol="org.apache.coyote.http11.Http11NioProtocol"
              SSLEnabled="true" maxPostSize="57671680">
     <SSLHostConfig protocols="TLSv1.2+TLSv1.3">
       <Certificate certificateKeystoreFile="conf/igamonmeet.p12"
                    certificateKeystoreType="PKCS12"
                    certificateKeystorePassword="<secret>"/>
     </SSLHostConfig>
   </Connector>
   ```
5. Copy `target/igamonmeet.war` to `webapps/` (or `webapps/ROOT.war` to serve at `/`).
6. Check `https://<host>:8443/igamonmeet/api/health` returns `{"status":"UP"}`.
