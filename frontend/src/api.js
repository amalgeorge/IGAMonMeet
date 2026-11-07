// Fetch wrapper: sends cookies and the CSRF token Spring Security expects (X-XSRF-TOKEN).
function csrfToken() {
  const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : null;
}

export async function ensureCsrf() {
  if (!csrfToken()) await fetch('api/auth/csrf', { credentials: 'same-origin' });
}

export async function api(path, { method = 'GET', json, form } = {}) {
  const headers = {};
  let body;
  if (method !== 'GET') {
    await ensureCsrf();
    headers['X-XSRF-TOKEN'] = csrfToken();
  }
  if (json) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(json); }
  if (form) { body = new URLSearchParams(form); }
  // Paths are resolved relative to the app's base, so it works under any Tomcat context path.
  const res = await fetch(path.replace(/^\//, ''), { method, headers, body, credentials: 'same-origin' });
  const data = res.status === 204 ? null : await res.json().catch(() => null);
  if (!res.ok) throw Object.assign(new Error(data?.message || 'Request failed'), { status: res.status, code: data?.error });
  return data;
}
