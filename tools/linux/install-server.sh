#!/usr/bin/env bash
# Installs or updates the IAM Monthly Meeting app on RHEL 8/9 (also Rocky Linux and AlmaLinux):
# OpenJDK 17, Tomcat 9 as a systemd service, HTTPS on 8443, fonts for slide text measurement,
# and the database settings for an EXISTING SQL Server (this script does not install SQL Server).
#
# Run as root:
#   sudo ./install-server.sh --war igamonmeet.war --db-host sql01.corp.local --db-name igamonmeet --db-user igamonmeet
#
# Safe to run again: installed parts are skipped, setenv.sh is rewritten and the WAR is redeployed.
# The database password is read from --db-password-file, the DB_PASSWORD environment variable or a prompt;
# it is never passed on the command line. See docs/deploy-linux.md.
set -euo pipefail

PREFIX=/opt/igamonmeet
WAR=""
FROM_CI=0
REPO=amalgeorge/IGAMonMeet
DB_HOST=""
DB_PORT=1433
DB_NAME=igamonmeet
DB_USER=igamonmeet
DB_PASSWORD_FILE=""
DB_TRUST_CERT=0
HOSTNAME_FQDN="$(hostname -f 2>/dev/null || cat /etc/hostname 2>/dev/null || echo localhost)"
KEYSTORE=""
KEYSTORE_PASSWORD_FILE=""
START=1
TIMEZONE=Asia/Singapore

usage() {
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
  cat <<'EOF'

Options:
  --war FILE                  WAR to deploy
  --from-ci                   download the latest WAR built by CI on main (needs gh, signed in)
  --db-host HOST              SQL Server host (required)
  --db-port PORT              default 1433
  --db-name NAME              default igamonmeet
  --db-user USER              default igamonmeet
  --db-password-file FILE     file holding only the password
  --db-trust-server-cert      accept the SQL Server certificate without checking it (TEST only)
  --hostname NAME             name in the self-signed certificate (default: hostname -f)
  --keystore FILE.p12         your own PKCS12 certificate instead of a self-signed one
  --keystore-password-file F  password of that keystore
  --prefix DIR                default /opt/igamonmeet
  --no-start                  install and deploy, but do not start the service
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --war) WAR="$2"; shift 2 ;;
    --from-ci) FROM_CI=1; shift ;;
    --repo) REPO="$2"; shift 2 ;;
    --db-host) DB_HOST="$2"; shift 2 ;;
    --db-port) DB_PORT="$2"; shift 2 ;;
    --db-name) DB_NAME="$2"; shift 2 ;;
    --db-user) DB_USER="$2"; shift 2 ;;
    --db-password-file) DB_PASSWORD_FILE="$2"; shift 2 ;;
    --db-trust-server-cert) DB_TRUST_CERT=1; shift ;;
    --hostname) HOSTNAME_FQDN="$2"; shift 2 ;;
    --keystore) KEYSTORE="$2"; shift 2 ;;
    --keystore-password-file) KEYSTORE_PASSWORD_FILE="$2"; shift 2 ;;
    --prefix) PREFIX="$2"; shift 2 ;;
    --no-start) START=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

step() { printf '\n\033[36m==> %s\033[0m\n' "$1"; }
ok()   { printf '    \033[32m%s\033[0m\n' "$1"; }
die()  { printf '\033[31mError: %s\033[0m\n' "$1" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run as root (sudo)."
[[ -n "$DB_HOST" ]] || die "--db-host is required."
[[ -n "$WAR" || $FROM_CI -eq 1 ]] || die "give --war FILE or --from-ci."
[[ -z "$WAR" || -f "$WAR" ]] || die "WAR not found: $WAR"
command -v dnf >/dev/null || die "dnf not found: this script is for RHEL 8/9, Rocky Linux or AlmaLinux."

if [[ -n "$DB_PASSWORD_FILE" ]]; then
  DB_PASSWORD="$(<"$DB_PASSWORD_FILE")"
elif [[ -z "${DB_PASSWORD:-}" ]]; then
  read -r -s -p "Password for SQL login '$DB_USER': " DB_PASSWORD; echo
fi
[[ -n "$DB_PASSWORD" ]] || die "empty database password."

TOMCAT="$PREFIX/tomcat"
BASE_URL="https://$HOSTNAME_FQDN:8443/igamonmeet"

# ---------------------------------------------------------------------------
step "Packages: OpenJDK 17, fonts, tools"
# Liberation fonts match Arial, Times New Roman and Courier New metrics; Carlito matches Calibri.
# Slide text is measured with the server's fonts (TextMeasurer), so these keep line breaks close to PowerPoint.
dnf -y -q install java-17-openjdk-headless fontconfig liberation-sans-fonts liberation-serif-fonts \
  liberation-mono-fonts tar gzip openssl >/dev/null   # curl(-minimal) ships with RHEL
dnf -y -q install google-carlito-fonts >/dev/null 2>&1 || echo "    (google-carlito-fonts not available; Calibri falls back to Liberation Sans)"
JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
"$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17' || die "Java 17 is not the default java (alternatives --config java)."
ok "Java 17 in $JAVA_HOME; fonts installed"

# ---------------------------------------------------------------------------
step "Service user and Tomcat 9"
id tomcat >/dev/null 2>&1 || useradd --system --home-dir "$TOMCAT" --shell /sbin/nologin tomcat
if systemctl is-active --quiet igamonmeet 2>/dev/null; then
  systemctl stop igamonmeet
  ok "Stopped running service"
fi
if [[ ! -x "$TOMCAT/bin/catalina.sh" ]]; then
  version="$(curl -fsSL https://dlcdn.apache.org/tomcat/tomcat-9/ | grep -oE 'v9\.0\.[0-9]+/' | tr -d 'v/' | sort -V | tail -1)"
  [[ -n "$version" ]] || die "could not find the current Tomcat 9 version on dlcdn.apache.org."
  tmp="$(mktemp -d)"
  url="https://dlcdn.apache.org/tomcat/tomcat-9/v$version/bin/apache-tomcat-$version.tar.gz"
  curl -fsSL -o "$tmp/tomcat.tar.gz" "$url"
  curl -fsSL -o "$tmp/tomcat.tar.gz.sha512" "$url.sha512"
  (cd "$tmp" && echo "$(cut -d' ' -f1 tomcat.tar.gz.sha512)  tomcat.tar.gz" | sha512sum -c --quiet) || die "Tomcat download checksum mismatch."
  mkdir -p "$PREFIX"
  tar -xzf "$tmp/tomcat.tar.gz" -C "$PREFIX"
  mv "$PREFIX/apache-tomcat-$version" "$TOMCAT"
  rm -rf "$TOMCAT"/webapps/{docs,examples,host-manager,manager} "$tmp"
  ok "Tomcat $version in $TOMCAT"
else
  ok "Tomcat already in $TOMCAT"
fi

# ---------------------------------------------------------------------------
step "HTTPS on 8443"
if [[ -n "$KEYSTORE" ]]; then
  [[ -f "$KEYSTORE" && -n "$KEYSTORE_PASSWORD_FILE" ]] || die "--keystore needs the file and --keystore-password-file."
  KS_PASSWORD="$(<"$KEYSTORE_PASSWORD_FILE")"
  install -m 600 "$KEYSTORE" "$TOMCAT/conf/igamonmeet.p12"
  ok "Using your certificate"
else
  KS_PASSWORD="$(openssl rand -hex 16)"
  rm -f "$TOMCAT/conf/igamonmeet.p12"
  "$JAVA_HOME/bin/keytool" -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -validity 825 \
    -dname "CN=$HOSTNAME_FQDN" -ext "SAN=dns:$HOSTNAME_FQDN,dns:localhost,ip:127.0.0.1" \
    -keystore "$TOMCAT/conf/igamonmeet.p12" -storetype PKCS12 -storepass "$KS_PASSWORD" -keypass "$KS_PASSWORD" -noprompt 2>/dev/null
  ok "Self-signed certificate for $HOSTNAME_FQDN (browsers warn once)"
fi
# server.xml: only the HTTPS connector; shutdown port disabled (systemd stops Tomcat with a signal).
cat > "$TOMCAT/conf/server.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!-- Written by install-server.sh -->
<Server port="-1" shutdown="SHUTDOWN">
  <Listener className="org.apache.catalina.startup.VersionLoggerListener" />
  <Listener className="org.apache.catalina.core.JreMemoryLeakPreventionListener" />
  <Listener className="org.apache.catalina.mbeans.GlobalResourcesLifecycleListener" />
  <Listener className="org.apache.catalina.core.ThreadLocalLeakPreventionListener" />
  <Service name="Catalina">
    <Connector port="8443" protocol="org.apache.coyote.http11.Http11NioProtocol"
               SSLEnabled="true" maxPostSize="57671680">
      <SSLHostConfig protocols="TLSv1.2+TLSv1.3">
        <Certificate certificateKeystoreFile="conf/igamonmeet.p12" certificateKeystoreType="PKCS12"
                     certificateKeystorePassword="$KS_PASSWORD" />
      </SSLHostConfig>
    </Connector>
    <Engine name="Catalina" defaultHost="localhost">
      <Host name="localhost" appBase="webapps" unpackWARs="true" autoDeploy="false">
        <Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
               prefix="localhost_access_log" suffix=".txt" pattern="%h %l %u %t &quot;%r&quot; %s %b" />
      </Host>
    </Engine>
  </Service>
</Server>
EOF
ok "server.xml written"

# ---------------------------------------------------------------------------
step "Database settings (remote SQL Server $DB_HOST:$DB_PORT/$DB_NAME)"
trust=false; [[ $DB_TRUST_CERT -eq 1 ]] && trust=true
# Single quotes keep the password literal for the shell that sources this file.
esc_pw="${DB_PASSWORD//\'/\'\\\'\'}"
cat > "$TOMCAT/bin/setenv.sh" <<EOF
# Written by install-server.sh. Readable by root and tomcat only: holds the database password.
export JAVA_HOME='$JAVA_HOME'
export DB_URL='jdbc:sqlserver://$DB_HOST:$DB_PORT;databaseName=$DB_NAME;encrypt=true;trustServerCertificate=$trust'
export DB_USER='$DB_USER'
export DB_PASSWORD='$esc_pw'
export CATALINA_OPTS="\$CATALINA_OPTS -Djava.awt.headless=true -Duser.timezone=UTC -Dfile.encoding=UTF-8 -Dapp.display-time-zone=$TIMEZONE"
EOF
chown root:tomcat "$TOMCAT/bin/setenv.sh"
chmod 640 "$TOMCAT/bin/setenv.sh"
ok "setenv.sh written (root and tomcat only)"
[[ $DB_TRUST_CERT -eq 1 ]] && echo "    TEST setting: the SQL Server certificate is not checked."

# ---------------------------------------------------------------------------
step "WAR"
if [[ $FROM_CI -eq 1 ]]; then
  command -v gh >/dev/null || die "--from-ci needs the GitHub CLI (gh), signed in. Or download the WAR and use --war."
  run_id="$(gh run list -R "$REPO" --branch main --workflow CI --status success --limit 1 --json databaseId --jq '.[0].databaseId')"
  [[ -n "$run_id" ]] || die "no successful CI run on main."
  dl="$(mktemp -d)"
  gh run download "$run_id" -R "$REPO" -n igamonmeet-war -D "$dl"
  WAR="$dl/igamonmeet.war"
  ok "Downloaded WAR from CI run $run_id"
fi
rm -rf "$TOMCAT/webapps/igamonmeet"
install -m 640 "$WAR" "$TOMCAT/webapps/igamonmeet.war"
# Code and config owned by root, readable by tomcat; tomcat writes only logs, temp, work and webapps.
chown -R root:tomcat "$TOMCAT"
chmod -R g+rX,o-rwx "$TOMCAT"
mkdir -p "$TOMCAT"/{logs,temp,work,webapps}
chown -R tomcat:tomcat "$TOMCAT"/{logs,temp,work,webapps}
chmod 640 "$TOMCAT/conf/igamonmeet.p12" "$TOMCAT/conf/server.xml" "$TOMCAT/bin/setenv.sh"
ok "Deployed $TOMCAT/webapps/igamonmeet.war"

# ---------------------------------------------------------------------------
step "systemd, firewall, SELinux"
mkdir -p /etc/systemd/system
cat > /etc/systemd/system/igamonmeet.service <<EOF
[Unit]
Description=IAM Monthly Meeting (Tomcat 9)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=tomcat
Group=tomcat
Environment=CATALINA_HOME=$TOMCAT
Environment=CATALINA_BASE=$TOMCAT
Environment=CATALINA_PID=$TOMCAT/temp/tomcat.pid
ExecStart=$TOMCAT/bin/catalina.sh run
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
UMask=0027
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full

[Install]
WantedBy=multi-user.target
EOF
if [[ -d /run/systemd/system ]]; then
  systemctl daemon-reload
  systemctl enable igamonmeet >/dev/null 2>&1
  ok "igamonmeet.service enabled"
else
  START=0
  ok "Unit written; systemd is not running here (container?), so the service is not enabled or started"
fi
if systemctl is-active --quiet firewalld 2>/dev/null; then
  firewall-cmd -q --permanent --add-port=8443/tcp && firewall-cmd -q --reload
  ok "firewalld: 8443/tcp open"
fi
if command -v restorecon >/dev/null && [[ "$(getenforce 2>/dev/null)" == "Enforcing" ]]; then
  restorecon -R "$PREFIX" /etc/systemd/system/igamonmeet.service
  ok "SELinux contexts restored (8443 is a standard http_port_t port)"
fi

# ---------------------------------------------------------------------------
if [[ $START -eq 1 ]]; then
  step "Starting"
  systemctl start igamonmeet
  echo "    Waiting for the app (first start runs the database migrations)..."
  for _ in $(seq 1 60); do
    sleep 5
    if curl -ks "https://localhost:8443/igamonmeet/api/health" | grep -q '"UP"'; then
      ok "$BASE_URL/api/health is UP"
      up=1; break
    fi
    systemctl is-active --quiet igamonmeet || die "the service stopped. See: journalctl -u igamonmeet -n 100, and $TOMCAT/logs."
  done
  [[ "${up:-0}" -eq 1 ]] || die "app did not report UP. See journalctl -u igamonmeet and $TOMCAT/logs."
fi

cat <<EOF

Done. App: $BASE_URL
First Admin: the DBA runs dbo.usp_AdminRecovery on $DB_NAME with @baseUrl = '$BASE_URL'
(docs/admin-recovery.md) and gives the Admin the one-time link.
Service: systemctl status|restart igamonmeet    Logs: journalctl -u igamonmeet, $TOMCAT/logs
EOF
