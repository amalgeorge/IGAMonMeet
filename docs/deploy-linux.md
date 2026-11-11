# Deploying on RHEL (Linux)

The WAR runs unchanged on Tomcat 9 on Linux. `tools/linux/install-server.sh` sets up a server on **RHEL 8 or 9** (also Rocky Linux and AlmaLinux) that connects to an **existing SQL Server**. Windows test machines keep using `tools/windows/Install-TestServer.ps1`.

## 1. What the DBA prepares

On the SQL Server:

1. A database, for example `igamonmeet`.
2. A SQL login and database user for the app, owner of that database (`db_owner`), or with rights to create and alter tables, views and procedures in it. Flyway creates and upgrades the schema at every start.
3. TCP access from the app server to the SQL Server port (default 1433).
4. A certificate the app server trusts. The connection always uses `encrypt=true`. For a test server with SQL Server's self-signed certificate, use `--db-trust-server-cert`.

## 2. Install

Copy the WAR (from the CI run's `igamonmeet-war` artifact) and the script to the server, then as root:

```bash
printf '%s' 'the-db-password' > /root/igamonmeet-db.pw && chmod 600 /root/igamonmeet-db.pw
sudo ./install-server.sh --war igamonmeet.war \
  --db-host sql01.corp.local --db-name igamonmeet --db-user igamonmeet \
  --db-password-file /root/igamonmeet-db.pw \
  --hostname meetings.corp.local
```

Without `--db-password-file` the script reads `DB_PASSWORD` from the environment or prompts. The password never goes on the command line.

The script:

| Part | Where |
|---|---|
| OpenJDK 17 (`java-17-openjdk-headless`) | RHEL AppStream |
| Fonts: Liberation (Arial, Times New Roman, Courier New metrics), Carlito (Calibri metrics) | needed for slide text measurement |
| Tomcat 9 (current release, or `--tomcat-version 9.0.x`), checksum and release signature verified against apache.org | `/opt/igamonmeet/tomcat` |
| HTTPS on 8443, TLS 1.2 and 1.3 only; HTTP connector and shutdown port off | `conf/server.xml` |
| Database settings, headless JVM, UTC | `bin/setenv.sh`, mode 640 root:tomcat |
| systemd service running as user `tomcat` | `igamonmeet.service` |
| firewalld port 8443/tcp, SELinux contexts | when firewalld or SELinux are on |

Your own certificate: `--keystore server.p12 --keystore-password-file ks.pw`. Otherwise a self-signed certificate for `--hostname` is created and browsers warn once.

## 3. First Admin

The DBA runs `dbo.usp_AdminRecovery` on the app database with `@baseUrl = 'https://<hostname>:8443/igamonmeet'` and hands the one-time link to the Admin. See [admin-recovery.md](admin-recovery.md).

## 4. Updating

Run the script again with the new WAR. It stops the service, rewrites `setenv.sh` and `server.xml`, redeploys and starts. Flyway applies new migrations at start.

## 5. Operating

```bash
systemctl status igamonmeet
systemctl restart igamonmeet
journalctl -u igamonmeet -n 200
ls /opt/igamonmeet/tomcat/logs
```

Health: `curl -k https://localhost:8443/igamonmeet/api/health` shows `"UP"`.

## 6. Checked in CI

Every build installs the WAR with this script in a Rocky Linux 9 container against SQL Server 2022 and checks the health endpoint (job `rhel-smoke`).
