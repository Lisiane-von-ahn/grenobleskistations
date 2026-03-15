import json
from pathlib import Path

import httpx


class ApiError(Exception):
    def __init__(self, message, status_code=None, payload=None):
        super().__init__(message)
        self.status_code = status_code
        self.payload = payload or {}


class GrenobleSkiApiClient:
    def __init__(self, base_url, data_dir):
        self.base_url = base_url.rstrip("/")
        self.data_dir = Path(data_dir)
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.session_file = self.data_dir / "session.json"
        self.token = None
        self._load_session()

    def _load_session(self):
        if not self.session_file.exists():
            return
        try:
            payload = json.loads(self.session_file.read_text(encoding="utf-8"))
        except Exception:
            return
        self.token = payload.get("token")

    def _save_session(self):
        payload = {"token": self.token}
        self.session_file.write_text(json.dumps(payload), encoding="utf-8")

    def clear_session(self):
        self.token = None
        if self.session_file.exists():
            self.session_file.unlink()

    def _headers(self, authenticated=False):
        headers = {"Accept": "application/json"}
        if authenticated and self.token:
            headers["Authorization"] = f"Token {self.token}"
        return headers

    async def _request(self, method, path, payload=None, authenticated=False):
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
    def _extract_list(data):
        if isinstance(data, list):
            return data
        if isinstance(data, dict) and "results" in data and isinstance(data["results"], list):
            return data["results"]
        return []

    async def login(self, email, password):
        data = await self._request("POST", "/auth/login/", payload={"email": email, "password": password})
        self.token = data.get("token")
        self._save_session()
        return data.get("user")

    async def register(self, email, password, first_name, last_name):
        data = await self._request(
            "POST",
            "/auth/register/",
            payload={
                "email": email,
                "password": password,
                "first_name": first_name,
                "last_name": last_name,
            },
        )
        self.token = data.get("token")
        self._save_session()
        return data.get("user")

    async def google_login(self, id_token):
        data = await self._request("POST", "/auth/google-login/", payload={"id_token": id_token})
        self.token = data.get("token")
        self._save_session()
        return data.get("user")

    async def me(self):
        data = await self._request("GET", "/auth/me/", authenticated=True)
        return data.get("user")

    async def logout(self):
        if self.token:
            await self._request("POST", "/auth/logout/", authenticated=True)
        self.clear_session()

    async def stations(self):
        data = await self._request("GET", "/skistations/", authenticated=True)
        return self._extract_list(data)

    async def bus_lines(self):
        data = await self._request("GET", "/buslines/", authenticated=True)
        return self._extract_list(data)

    async def services(self):
        data = await self._request("GET", "/servicestores/", authenticated=True)
        return self._extract_list(data)

    async def marketplace(self):
        data = await self._request("GET", "/skimaterial/", authenticated=True)
        return self._extract_list(data)
