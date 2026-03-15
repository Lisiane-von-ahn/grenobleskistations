import json
from pathlib import Path
from urllib.parse import urlsplit

import httpx


class ApiError(Exception):
    def __init__(self, message, status_code=None, payload=None):
        super().__init__(message)
        self.status_code = status_code
        self.payload = payload or {}


class GrenobleSkiApiClient:
    def __init__(self, base_url, data_dir):
        self.base_url = base_url.rstrip("/")
        self.site_url = self._derive_site_url(self.base_url)
        self.swagger_url = self._derive_swagger_url(self.base_url)
        self.data_dir = Path(data_dir)
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.session_file = self.data_dir / "session.json"
        self.token = None
        self.available_paths = set()
        self.schema_loaded = False
        self.login_email = None
        self.login_password = None
        self._load_session()

    @staticmethod
    def _derive_swagger_url(base_url):
        parts = urlsplit(base_url)
        return f"{parts.scheme}://{parts.netloc}/swagger/?format=openapi"

    @staticmethod
    def _derive_site_url(base_url):
        parts = urlsplit(base_url)
        return f"{parts.scheme}://{parts.netloc}"

    def website_url(self, path):
        normalized_path = path if path.startswith("/") else f"/{path}"
        return f"{self.site_url}{normalized_path}"

    def _load_session(self):
        if not self.session_file.exists():
            return
        try:
            payload = json.loads(self.session_file.read_text(encoding="utf-8"))
        except Exception:
            return
        self.token = payload.get("token")
        self.login_email = payload.get("login_email")

    def _save_session(self):
        payload = {"token": self.token, "login_email": self.login_email}
        self.session_file.write_text(json.dumps(payload), encoding="utf-8")

    def clear_session(self):
        self.token = None
        self.login_email = None
        self.login_password = None
        if self.session_file.exists():
            self.session_file.unlink()

    def _headers(self, authenticated=False):
        headers = {"Accept": "application/json"}
        if authenticated and self.token:
            headers["Authorization"] = f"Token {self.token}"
        return headers

    @staticmethod
    def _extract_list(data):
        if isinstance(data, list):
            return data
        if isinstance(data, dict) and "results" in data and isinstance(data["results"], list):
            return data["results"]
        return []

    def _path(self, *candidates):
        if self.available_paths:
            for candidate in candidates:
                if candidate in self.available_paths:
                    return candidate
        return candidates[0] if candidates else ""

    def _supports(self, *candidates):
        if not self.available_paths:
            return True
        return any(candidate in self.available_paths for candidate in candidates)

    async def _ensure_schema_loaded(self):
        if self.schema_loaded:
            return

        self.schema_loaded = True
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                response = await client.get(self.swagger_url, headers={"Accept": "application/json"})
            if response.status_code != 200:
                return
            data = response.json()
            self.available_paths = set(data.get("paths", {}).keys())
        except Exception:
            self.available_paths = set()

    async def _request(self, method, path, payload=None, authenticated=False):
        await self._ensure_schema_loaded()
        url = f"{self.base_url}{path}"
        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.request(
                method,
                url,
                json=payload,
                headers=self._headers(authenticated=authenticated),
            )

        try:
            data = response.json()
        except Exception:
            data = {}

        if response.status_code >= 400:
            message = data.get("error") or data.get("detail") or f"Request failed ({response.status_code})"
            raise ApiError(message, status_code=response.status_code, payload=data)
        return data

    @staticmethod
    def _extract_user(data):
        if not isinstance(data, dict):
            return None
        if isinstance(data.get("user"), dict):
            return data.get("user")
        if "email" in data or "username" in data:
            return data
        return None

    async def get_capabilities(self):
        await self._ensure_schema_loaded()
        return {
            "has_messages": self._supports("/messages/"),
            "has_stories": self._supports("/skistories/"),
            "has_partners": self._supports("/skipartnerposts/"),
            "has_instructors": self._supports("/instructorservices/", "/instructorprofiles/"),
            "has_auth_google": self._supports("/auth/google-login/"),
            "has_auth_register": self._supports("/auth/register/", "/userview/register/"),
        }

    async def login(self, email, password):
        self.login_email = email
        self.login_password = password
        path = self._path("/auth/login/", "/login/")
        data = await self._request("POST", path, payload={"email": email, "password": password})
        self.token = data.get("token")
        if not self.token:
            raise ApiError("This server did not return an API token.")
        self._save_session()
        user = self._extract_user(data)
        if not user:
            try:
                user = await self.me()
            except Exception:
                user = {"email": email}
        return user

    async def register(self, email, password, first_name, last_name):
        self.login_email = email
        self.login_password = password
        path = self._path("/auth/register/", "/userview/register/")
        data = await self._request(
            "POST",
            path,
            payload={
                "email": email,
                "password": password,
                "first_name": first_name,
                "last_name": last_name,
            },
        )
        self.token = data.get("token")
        if not self.token:
            # Legacy registration endpoints do not always return token.
            return await self.login(email=email, password=password)
        self._save_session()
        user = self._extract_user(data)
        return user or await self.me()

    async def google_login(self, id_token):
        if not self._supports("/auth/google-login/"):
            raise ApiError("Google login endpoint is not available on this server.")
        data = await self._request("POST", "/auth/google-login/", payload={"id_token": id_token})
        self.token = data.get("token")
        if not self.token:
            raise ApiError("Google login succeeded but no API token was returned.")
        self._save_session()
        user = self._extract_user(data)
        return user or await self.me()

    async def me(self):
        if self._supports("/auth/me/"):
            data = await self._request("GET", "/auth/me/", authenticated=True)
            user = self._extract_user(data)
            if user:
                return user

        if self._supports("/userprofile/me/"):
            data = await self._request("GET", "/userprofile/me/", authenticated=True)
            if isinstance(data, list) and data:
                profile = data[0]
            elif isinstance(data, dict):
                profile = data
            else:
                profile = {}
            user = profile.get("user") if isinstance(profile.get("user"), dict) else None
            if user:
                return user

        raise ApiError("Unable to fetch current user profile from this server.")

    async def logout(self):
        if self.token and self._supports("/auth/logout/"):
            try:
                await self._request("POST", "/auth/logout/", authenticated=True)
            except Exception:
                pass
        self.clear_session()

    async def _list_resource(self, *paths):
        path = self._path(*paths)
        data = await self._request("GET", path, authenticated=True)
        return self._extract_list(data)

    async def stations(self):
        return await self._list_resource("/skistations/")

    async def bus_lines(self):
        return await self._list_resource("/buslines/")

    async def services(self):
        return await self._list_resource("/servicestores/")

    async def marketplace(self):
        return await self._list_resource("/skimaterial/")

    async def circuits(self):
        return await self._list_resource("/skicircuits/")

    async def messages(self):
        return await self._list_resource("/messages/")

    async def create_message(self, recipient, subject, body):
        path = self._path("/messages/")
        return await self._request(
            "POST",
            path,
            payload={"recipient": recipient, "subject": subject, "body": body},
            authenticated=True,
        )

    async def stories(self):
        return await self._list_resource("/skistories/")

    async def ski_partners(self):
        return await self._list_resource("/skipartnerposts/")

    async def instructor_services(self):
        return await self._list_resource("/instructorservices/")
