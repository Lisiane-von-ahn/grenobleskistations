import asyncio
import os
from pathlib import Path

import toga
from toga.style import Pack
from toga.style.pack import CENTER, COLUMN, ROW

from .api_client import ApiError, GrenobleSkiApiClient
from .i18n import tr
from .theme import COLORS


class GrenobleSkiMobile(toga.App):
    def startup(self):
        self.lang = "fr"
        self.user = None
        self.stations_data = []
        self.bus_data = []
        self.services_data = []
        self.market_data = []

        default_api = os.getenv("GRENOBLESKI_API_URL", "http://127.0.0.1:8000/api")
        self.api = GrenobleSkiApiClient(base_url=default_api, data_dir=self.paths.data)

        self.main_window = toga.MainWindow(title=self.t("app_title"))
        self.header_title = toga.Label(
            self.t("app_title"),
            style=Pack(font_size=22, font_weight="bold", color=COLORS["header_text"]),
        )
        self.header_subtitle = toga.Label(
            self.t("tagline"),
            style=Pack(font_size=11, color=COLORS["header_text"], padding_top=4),
        )

        logo_path = Path(__file__).resolve().parent / "resources" / "logo.png"
        self.logo_view = toga.ImageView(
            toga.Image(str(logo_path)),
            style=Pack(width=56, height=56, padding_right=10),
        )

        title_box = toga.Box(
            children=[self.header_title, self.header_subtitle],
            style=Pack(direction=COLUMN, flex=1),
        )

        self.lang_button = toga.Button(
            "FR / EN",
            on_press=self.on_toggle_language,
            style=Pack(
                color=COLORS["accent_text"],
                background_color=COLORS["accent"],
                padding_left=12,
                padding_right=12,
                padding_top=8,
                padding_bottom=8,
            ),
        )

        header_row = toga.Box(
            children=[self.logo_view, title_box, self.lang_button],
            style=Pack(
                direction=ROW,
                alignment=CENTER,
                background_color=COLORS["header_bg"],
                padding=16,
            ),
        )

        self.content = toga.Box(style=Pack(direction=COLUMN, flex=1, padding=18))
        self.status_label = toga.Label(
            self.t("status_ready"),
            style=Pack(color=COLORS["muted_text"], padding=8),
        )

        self.root = toga.Box(
            children=[header_row, self.content, self.status_label],
            style=Pack(direction=COLUMN, flex=1, background_color=COLORS["page_bg"]),
        )

        self.main_window.content = self.root
        self.main_window.show()

        self._build_auth_view()
        if self.api.token:
            asyncio.create_task(self._resume_session())

    def t(self, key, **kwargs):
        return tr(self.lang, key, **kwargs)

    def _set_status(self, key, **kwargs):
        self.status_label.text = self.t(key, **kwargs)

    def _clear_box(self, box):
        while box.children:
            box.remove(box.children[0])

    def on_toggle_language(self, widget):
        self.lang = "en" if self.lang == "fr" else "fr"
        self.main_window.title = self.t("app_title")
        self.header_title.text = self.t("app_title")
        self.header_subtitle.text = self.t("tagline")
        self._set_status("status_ready")

        if self.user:
            self._build_app_view()
            self._refresh_summary()
            self._render_stations_list()
            self._render_bus_list()
            self._render_services_list()
            self._render_market_list()
        else:
            self._build_auth_view()

    def _build_auth_view(self):
        self._clear_box(self.content)

        title = toga.Label(
            self.t("login_title"),
            style=Pack(font_size=20, font_weight="bold", color=COLORS["title_text"], padding_bottom=10),
        )

        self.email_input = toga.TextInput(placeholder=self.t("email"), style=Pack(padding_bottom=8))
        self.password_input = toga.PasswordInput(placeholder=self.t("password"), style=Pack(padding_bottom=8))
        self.first_name_input = toga.TextInput(placeholder=self.t("first_name"), style=Pack(padding_bottom=8))
        self.last_name_input = toga.TextInput(placeholder=self.t("last_name"), style=Pack(padding_bottom=8))
        self.google_token_input = toga.MultilineTextInput(
            placeholder=self.t("google_token"),
            style=Pack(height=110, padding_top=8),
        )

        login_button = toga.Button(
            self.t("login"),
            on_press=self.on_login,
            style=Pack(background_color=COLORS["accent"], color=COLORS["accent_text"], padding=10, flex=1),
        )
        register_button = toga.Button(
            self.t("register"),
            on_press=self.on_register,
            style=Pack(background_color=COLORS["header_bg"], color=COLORS["accent_text"], padding=10, flex=1),
        )
        auth_buttons = toga.Box(
            children=[login_button, register_button],
            style=Pack(direction=ROW, padding_top=8, padding_bottom=8),
        )

        google_help = toga.Label(
            self.t("google_help"),
            style=Pack(color=COLORS["muted_text"], font_size=11, padding_top=4),
        )
        google_button = toga.Button(
            self.t("google_login"),
            on_press=self.on_google_login,
            style=Pack(background_color=COLORS["accent"], color=COLORS["accent_text"], padding=10, padding_top=10),
        )

        auth_card = toga.Box(
            children=[
                title,
                self.email_input,
                self.password_input,
                self.first_name_input,
                self.last_name_input,
                auth_buttons,
                self.google_token_input,
                google_help,
                google_button,
            ],
            style=Pack(direction=COLUMN, background_color=COLORS["card_bg"], padding=16),
        )

        self.content.add(auth_card)

    def _build_app_view(self):
        self._clear_box(self.content)

        self.nav_buttons = {}
        nav_bar = toga.Box(style=Pack(direction=ROW, padding_bottom=10))
        for key in ["home", "stations", "bus", "services", "marketplace", "profile"]:
            button = toga.Button(
                self.t(f"nav_{key}"),
                on_press=self.on_nav_press,
                id=key,
                style=Pack(
                    background_color=COLORS["header_bg"],
                    color=COLORS["accent_text"],
                    padding_left=10,
                    padding_right=10,
                    padding_top=8,
                    padding_bottom=8,
                ),
            )
            self.nav_buttons[key] = button
            nav_bar.add(button)

        self.section_container = toga.Box(style=Pack(direction=COLUMN, flex=1))
        self.content.add(nav_bar)
        self.content.add(self.section_container)

        self._prepare_sections()
        self._show_section("home")

    def _prepare_sections(self):
        self.home_summary = toga.Label("", style=Pack(color=COLORS["title_text"], padding=8))
        self.home_counts = toga.Label("", style=Pack(color=COLORS["muted_text"], padding=8))
        self.home_section = toga.Box(
            children=[
                self.home_summary,
                toga.Label(self.t("home_intro"), style=Pack(color=COLORS["muted_text"], padding=8)),
                self.home_counts,
                toga.Button(self.t("refresh"), on_press=self.on_refresh_all, style=Pack(width=150, margin=8)),
            ],
            style=Pack(direction=COLUMN),
        )

        self.station_search = toga.TextInput(placeholder=self.t("search"), style=Pack(flex=1, padding_right=8))
        station_refresh = toga.Button(self.t("refresh"), on_press=self.on_refresh_stations, style=Pack(width=120))
        station_top = toga.Box(children=[self.station_search, station_refresh], style=Pack(direction=ROW, padding_bottom=8))
        self.stations_list_box = toga.Box(style=Pack(direction=COLUMN))
        self.stations_section = toga.Box(
            children=[station_top, toga.ScrollContainer(content=self.stations_list_box, style=Pack(flex=1))],
            style=Pack(direction=COLUMN, flex=1),
        )

        bus_refresh = toga.Button(self.t("refresh"), on_press=self.on_refresh_bus, style=Pack(width=120))
        self.bus_list_box = toga.Box(style=Pack(direction=COLUMN))
        self.bus_section = toga.Box(
            children=[bus_refresh, toga.ScrollContainer(content=self.bus_list_box, style=Pack(flex=1, padding_top=8))],
            style=Pack(direction=COLUMN, flex=1),
        )

        services_refresh = toga.Button(self.t("refresh"), on_press=self.on_refresh_services, style=Pack(width=120))
        self.services_list_box = toga.Box(style=Pack(direction=COLUMN))
        self.services_section = toga.Box(
            children=[services_refresh, toga.ScrollContainer(content=self.services_list_box, style=Pack(flex=1, padding_top=8))],
            style=Pack(direction=COLUMN, flex=1),
        )

        market_refresh = toga.Button(self.t("refresh"), on_press=self.on_refresh_market, style=Pack(width=120))
        self.market_list_box = toga.Box(style=Pack(direction=COLUMN))
        self.market_section = toga.Box(
            children=[market_refresh, toga.ScrollContainer(content=self.market_list_box, style=Pack(flex=1, padding_top=8))],
            style=Pack(direction=COLUMN, flex=1),
        )

        self.profile_name = toga.Label("", style=Pack(color=COLORS["title_text"], padding=8))
        self.profile_email = toga.Label("", style=Pack(color=COLORS["muted_text"], padding=8))
        self.profile_section = toga.Box(
            children=[
                self.profile_name,
                self.profile_email,
                toga.Button(
                    self.t("logout"),
                    on_press=self.on_logout,
                    style=Pack(width=170, background_color=COLORS["warning"], color=COLORS["accent_text"], margin=8),
                ),
            ],
            style=Pack(direction=COLUMN),
        )

        self.sections = {
            "home": self.home_section,
            "stations": self.stations_section,
            "bus": self.bus_section,
            "services": self.services_section,
            "marketplace": self.market_section,
            "profile": self.profile_section,
        }

    def _show_section(self, key):
        self.current_section = key
        self._clear_box(self.section_container)
        self.section_container.add(self.sections[key])

    def _refresh_summary(self):
        full_name = f"{self.user.get('first_name', '')} {self.user.get('last_name', '')}".strip()
        name = full_name or self.user.get("email", "")
        self.home_summary.text = self.t("welcome", name=name)
        self.home_counts.text = " | ".join(
            [
                self.t("stations_count", count=len(self.stations_data)),
                self.t("bus_count", count=len(self.bus_data)),
                self.t("services_count", count=len(self.services_data)),
                self.t("market_count", count=len(self.market_data)),
            ]
        )
        self.profile_name.text = name
        self.profile_email.text = self.user.get("email", "")

    def _make_card(self, title, lines):
        card = toga.Box(style=Pack(direction=COLUMN, background_color=COLORS["card_bg"], padding=12, padding_bottom=14))
        card.add(toga.Label(title, style=Pack(color=COLORS["title_text"], font_size=13, font_weight="bold", padding_bottom=4)))
        for line in lines:
            card.add(toga.Label(line, style=Pack(color=COLORS["muted_text"], font_size=11, padding_bottom=2)))
        return card

    def _render_stations_list(self):
        self._clear_box(self.stations_list_box)
        query = (self.station_search.value or "").strip().lower()
        data = self.stations_data
        if query:
            data = [item for item in data if query in (item.get("name") or "").lower()]

        if not data:
            self.stations_list_box.add(toga.Label(self.t("empty"), style=Pack(color=COLORS["muted_text"], padding=8)))
            return

        for station in data:
            title = station.get("name") or "Station"
            lines = [
                f"{self.t('city')}: Grenoble",
                f"{self.t('station')}: {station.get('distanceFromGrenoble', '-')} km",
                f"Altitude: {station.get('altitude', '-')}",
            ]
            self.stations_list_box.add(self._make_card(title, lines))

    def _render_bus_list(self):
        self._clear_box(self.bus_list_box)
        if not self.bus_data:
            self.bus_list_box.add(toga.Label(self.t("empty"), style=Pack(color=COLORS["muted_text"], padding=8)))
            return

        for line in self.bus_data:
            title = line.get("bus_number") or "Bus"
            lines = [
                f"{line.get('departure_stop', '-') } -> {line.get('arrival_stop', '-')}",
                f"Freq: {line.get('frequency', '-')}",
                f"Travel: {line.get('travel_time', '-')}",
            ]
            self.bus_list_box.add(self._make_card(title, lines))

    def _render_services_list(self):
        self._clear_box(self.services_list_box)
        if not self.services_data:
            self.services_list_box.add(toga.Label(self.t("empty"), style=Pack(color=COLORS["muted_text"], padding=8)))
            return

        for service in self.services_data:
            title = service.get("name") or "Service"
            lines = [
                f"Type: {service.get('type', '-')}",
                f"{self.t('hours')}: {service.get('opening_hours', '-')}",
            ]
            self.services_list_box.add(self._make_card(title, lines))

    def _render_market_list(self):
        self._clear_box(self.market_list_box)
        if not self.market_data:
            self.market_list_box.add(toga.Label(self.t("empty"), style=Pack(color=COLORS["muted_text"], padding=8)))
            return

        for listing in self.market_data:
            title = listing.get("title") or "Listing"
            lines = [
                f"{self.t('city')}: {listing.get('city', '-')}",
                f"{self.t('price')}: {listing.get('price', '-')}",
                f"{self.t('difficulty')}: {listing.get('condition', '-')}",
            ]
            self.market_list_box.add(self._make_card(title, lines))

    async def _resume_session(self):
        try:
            self._set_status("status_loading")
            self.user = await self.api.me()
            self._build_app_view()
            await self._load_all_data()
            self._set_status("status_ready")
        except Exception:
            self.api.clear_session()
            self.user = None
            self._build_auth_view()
            self._set_status("status_ready")

    async def _load_all_data(self):
        self._set_status("status_loading")
        try:
            stations, bus, services, market = await asyncio.gather(
                self.api.stations(),
                self.api.bus_lines(),
                self.api.services(),
                self.api.marketplace(),
            )
            self.stations_data = stations
            self.bus_data = bus
            self.services_data = services
            self.market_data = market
            self._refresh_summary()
            self._render_stations_list()
            self._render_bus_list()
            self._render_services_list()
            self._render_market_list()
            self._set_status("status_ready")
        except ApiError as exc:
            if exc.status_code == 401:
                self.user = None
                self.api.clear_session()
                self._build_auth_view()
            self._set_status("status_error", message=str(exc))

    def on_nav_press(self, widget):
        self._show_section(widget.id)

    def on_login(self, widget):
        asyncio.create_task(self._do_login())

    async def _do_login(self):
        email = (self.email_input.value or "").strip().lower()
        password = self.password_input.value or ""
        if not email or not password:
            self._set_status("status_error", message="Missing email/password")
            return

        self._set_status("status_loading")
        try:
            self.user = await self.api.login(email=email, password=password)
            self._build_app_view()
            await self._load_all_data()
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_register(self, widget):
        asyncio.create_task(self._do_register())

    async def _do_register(self):
        email = (self.email_input.value or "").strip().lower()
        password = self.password_input.value or ""
        first_name = (self.first_name_input.value or "").strip()
        last_name = (self.last_name_input.value or "").strip()

        if not email or not password:
            self._set_status("status_error", message="Missing email/password")
            return

        self._set_status("status_loading")
        try:
            self.user = await self.api.register(
                email=email,
                password=password,
                first_name=first_name,
                last_name=last_name,
            )
            self._build_app_view()
            await self._load_all_data()
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_google_login(self, widget):
        asyncio.create_task(self._do_google_login())

    async def _do_google_login(self):
        id_token = (self.google_token_input.value or "").strip()
        if not id_token:
            self._set_status("status_error", message="Missing Google ID token")
            return

        self._set_status("status_loading")
        try:
            self.user = await self.api.google_login(id_token=id_token)
            self._build_app_view()
            await self._load_all_data()
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_refresh_all(self, widget):
        asyncio.create_task(self._load_all_data())

    def on_refresh_stations(self, widget):
        asyncio.create_task(self._refresh_stations_only())

    async def _refresh_stations_only(self):
        self._set_status("status_loading")
        try:
            self.stations_data = await self.api.stations()
            self._render_stations_list()
            self._refresh_summary()
            self._set_status("status_ready")
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_refresh_bus(self, widget):
        asyncio.create_task(self._refresh_bus_only())

    async def _refresh_bus_only(self):
        self._set_status("status_loading")
        try:
            self.bus_data = await self.api.bus_lines()
            self._render_bus_list()
            self._refresh_summary()
            self._set_status("status_ready")
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_refresh_services(self, widget):
        asyncio.create_task(self._refresh_services_only())

    async def _refresh_services_only(self):
        self._set_status("status_loading")
        try:
            self.services_data = await self.api.services()
            self._render_services_list()
            self._refresh_summary()
            self._set_status("status_ready")
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_refresh_market(self, widget):
        asyncio.create_task(self._refresh_market_only())

    async def _refresh_market_only(self):
        self._set_status("status_loading")
        try:
            self.market_data = await self.api.marketplace()
            self._render_market_list()
            self._refresh_summary()
            self._set_status("status_ready")
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_logout(self, widget):
        asyncio.create_task(self._do_logout())

    async def _do_logout(self):
        self._set_status("status_loading")
        try:
            await self.api.logout()
        except Exception:
            self.api.clear_session()

        self.user = None
        self.stations_data = []
        self.bus_data = []
        self.services_data = []
        self.market_data = []
        self._build_auth_view()
        self._set_status("status_ready")


def main():
    return GrenobleSkiMobile()
