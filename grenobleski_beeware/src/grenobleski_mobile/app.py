import asyncio
import os
import webbrowser
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
        self.capabilities = {}

        self.stations_data = []
        self.bus_data = []
        self.services_data = []
        self.market_data = []
        self.circuits_data = []
        self.messages_data = []
        self.stories_data = []
        self.partners_data = []
        self.instructors_data = []

        default_api = os.getenv("GRENOBLESKI_API_URL", "https://www.grenobleski.fr/api")
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
            self._render_all_sections()
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
            style=Pack(height=90, padding_top=8),
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
        auth_buttons = toga.Box(children=[login_button, register_button], style=Pack(direction=ROW, padding_top=8, padding_bottom=8))

        google_help = toga.Label(self.t("google_help"), style=Pack(color=COLORS["muted_text"], font_size=11, padding_top=4))
        google_button = toga.Button(
            self.t("google_login"),
            on_press=self.on_google_login,
            style=Pack(background_color=COLORS["accent"], color=COLORS["accent_text"], padding=10),
        )
        google_browser_button = toga.Button(
            self.t("google_browser_login"),
            on_press=self.on_google_browser_login,
            style=Pack(background_color=COLORS["header_bg"], color=COLORS["accent_text"], padding=10, padding_top=8),
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
                google_browser_button,
            ],
            style=Pack(direction=COLUMN, background_color=COLORS["card_bg"], padding=16),
        )

        self.content.add(auth_card)

    def _compute_nav_keys(self):
        keys = ["home", "stations", "bus", "services", "marketplace"]
        if self.capabilities.get("has_stories"):
            keys.append("stories")
        if self.capabilities.get("has_partners"):
            keys.append("partners")
        if self.capabilities.get("has_instructors"):
            keys.append("instructors")
        if self.capabilities.get("has_messages"):
            keys.append("messages")
        keys.append("profile")
        return keys

    def _build_app_view(self):
        self._clear_box(self.content)

        self.nav_buttons = {}
        nav_bar = toga.Box(style=Pack(direction=ROW, padding_bottom=10))
        for key in self._compute_nav_keys():
            button = toga.Button(
                self.t(f"nav_{key}"),
                on_press=self.on_nav_press,
                style=Pack(
                    background_color=COLORS["header_bg"],
                    color=COLORS["accent_text"],
                    padding_left=10,
                    padding_right=10,
                    padding_top=8,
                    padding_bottom=8,
                ),
            )
            button.nav_key = key
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
                toga.Button(self.t("refresh"), on_press=self.on_refresh_all, style=Pack(width=170, padding=8)),
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

        stories_refresh = toga.Button(self.t("refresh"), on_press=self.on_refresh_stories, style=Pack(width=120))
        self.stories_list_box = toga.Box(style=Pack(direction=COLUMN))
        self.stories_section = toga.Box(
            children=[stories_refresh, toga.ScrollContainer(content=self.stories_list_box, style=Pack(flex=1, padding_top=8))],
            style=Pack(direction=COLUMN, flex=1),
        )

        partners_refresh = toga.Button(self.t("refresh"), on_press=self.on_refresh_partners, style=Pack(width=120))
        self.partners_list_box = toga.Box(style=Pack(direction=COLUMN))
        self.partners_section = toga.Box(
            children=[partners_refresh, toga.ScrollContainer(content=self.partners_list_box, style=Pack(flex=1, padding_top=8))],
            style=Pack(direction=COLUMN, flex=1),
        )

        instructors_refresh = toga.Button(self.t("refresh"), on_press=self.on_refresh_instructors, style=Pack(width=120))
        self.instructors_list_box = toga.Box(style=Pack(direction=COLUMN))
        self.instructors_section = toga.Box(
            children=[instructors_refresh, toga.ScrollContainer(content=self.instructors_list_box, style=Pack(flex=1, padding_top=8))],
            style=Pack(direction=COLUMN, flex=1),
        )

        self.msg_recipient_input = toga.TextInput(placeholder=self.t("recipient_id"), style=Pack(padding_bottom=6))
        self.msg_subject_input = toga.TextInput(placeholder=self.t("subject"), style=Pack(padding_bottom=6))
        self.msg_body_input = toga.MultilineTextInput(placeholder=self.t("message_body"), style=Pack(height=80, padding_bottom=6))
        send_message_button = toga.Button(self.t("send_message"), on_press=self.on_send_message, style=Pack(width=130, padding_bottom=6))
        messages_refresh = toga.Button(self.t("refresh"), on_press=self.on_refresh_messages, style=Pack(width=120))
        messages_actions = toga.Box(children=[send_message_button, messages_refresh], style=Pack(direction=ROW, padding_bottom=6))
        self.messages_list_box = toga.Box(style=Pack(direction=COLUMN))
        self.messages_section = toga.Box(
            children=[
                self.msg_recipient_input,
                self.msg_subject_input,
                self.msg_body_input,
                messages_actions,
                toga.ScrollContainer(content=self.messages_list_box, style=Pack(flex=1, padding_top=8)),
            ],
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
                    style=Pack(width=170, background_color=COLORS["warning"], color=COLORS["accent_text"], padding=8),
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
            "stories": self.stories_section,
            "partners": self.partners_section,
            "instructors": self.instructors_section,
            "messages": self.messages_section,
            "profile": self.profile_section,
        }

    def _show_section(self, key):
        self.current_section = key
        self._clear_box(self.section_container)
        section = self.sections.get(key)
        if section is None:
            section = toga.Box(children=[toga.Label(self.t("api_unavailable"), style=Pack(color=COLORS["muted_text"], padding=10))])
        self.section_container.add(section)

    def _refresh_summary(self):
        full_name = f"{self.user.get('first_name', '')} {self.user.get('last_name', '')}".strip()
        name = full_name or self.user.get("email", "")

        counters = [
            self.t("stations_count", count=len(self.stations_data)),
            self.t("bus_count", count=len(self.bus_data)),
            self.t("services_count", count=len(self.services_data)),
            self.t("market_count", count=len(self.market_data)),
        ]
        if self.capabilities.get("has_stories"):
            counters.append(self.t("stories_count", count=len(self.stories_data)))
        if self.capabilities.get("has_partners"):
            counters.append(self.t("partners_count", count=len(self.partners_data)))
        if self.capabilities.get("has_instructors"):
            counters.append(self.t("instructors_count", count=len(self.instructors_data)))
        if self.capabilities.get("has_messages"):
            counters.append(self.t("messages_count", count=len(self.messages_data)))

        self.home_summary.text = self.t("welcome", name=name)
        self.home_counts.text = " | ".join(counters)
        self.profile_name.text = name
        self.profile_email.text = self.user.get("email", "")

    def _make_card(self, title, lines):
        card = toga.Box(style=Pack(direction=COLUMN, background_color=COLORS["card_bg"], padding=12, padding_bottom=14))
        card.add(
            toga.Label(
                title,
                style=Pack(color=COLORS["title_text"], font_size=13, font_weight="bold", padding_bottom=4),
            )
        )
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
                f"{line.get('departure_stop', '-')} -> {line.get('arrival_stop', '-')}",
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

    def _render_stories_list(self):
        self._clear_box(self.stories_list_box)
        if not self.stories_data:
            self.stories_list_box.add(toga.Label(self.t("empty"), style=Pack(color=COLORS["muted_text"], padding=8)))
            return

        for story in self.stories_data:
            title = story.get("caption") or f"Story #{story.get('id', '-') }"
            lines = [
                f"{self.t('published')}: {story.get('created_at', '-')}",
                f"{self.t('expires')}: {story.get('expires_at', '-')}",
            ]
            self.stories_list_box.add(self._make_card(title, lines))

    def _render_partners_list(self):
        self._clear_box(self.partners_list_box)
        if not self.partners_data:
            self.partners_list_box.add(toga.Label(self.t("empty"), style=Pack(color=COLORS["muted_text"], padding=8)))
            return

        for post in self.partners_data:
            title = post.get("title") or "Partner"
            lines = [
                f"{self.t('city')}: {post.get('city', '-')}",
                f"{self.t('station')}: {post.get('ski_station', '-')}",
                f"Level: {post.get('skill_level', '-')}",
            ]
            self.partners_list_box.add(self._make_card(title, lines))

    def _render_instructors_list(self):
        self._clear_box(self.instructors_list_box)
        if not self.instructors_data:
            self.instructors_list_box.add(toga.Label(self.t("empty"), style=Pack(color=COLORS["muted_text"], padding=8)))
            return

        for item in self.instructors_data:
            title = item.get("title") or "Instructor Service"
            lines = [
                f"Duration: {item.get('duration_minutes', '-')}",
                f"Amount: {item.get('amount', '-')}",
                f"Group: {item.get('max_group_size', '-')}",
            ]
            self.instructors_list_box.add(self._make_card(title, lines))

    def _render_messages_list(self):
        self._clear_box(self.messages_list_box)
        if not self.messages_data:
            self.messages_list_box.add(toga.Label(self.t("empty"), style=Pack(color=COLORS["muted_text"], padding=8)))
            return

        for msg in self.messages_data:
            title = msg.get("subject") or "Message"
            lines = [
                f"From: {msg.get('sender', '-')}",
                f"To: {msg.get('recipient', '-')}",
                (msg.get("body") or "")[:80],
            ]
            self.messages_list_box.add(self._make_card(title, lines))

    def _render_all_sections(self):
        self._render_stations_list()
        self._render_bus_list()
        self._render_services_list()
        self._render_market_list()
        if self.capabilities.get("has_stories"):
            self._render_stories_list()
        if self.capabilities.get("has_partners"):
            self._render_partners_list()
        if self.capabilities.get("has_instructors"):
            self._render_instructors_list()
        if self.capabilities.get("has_messages"):
            self._render_messages_list()

    async def _load_capabilities(self):
        self.capabilities = await self.api.get_capabilities()

    async def _resume_session(self):
        try:
            self._set_status("status_loading")
            self.user = await self.api.me()
            await self._load_capabilities()
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

        jobs = [
            ("stations_data", self.api.stations),
            ("bus_data", self.api.bus_lines),
            ("services_data", self.api.services),
            ("market_data", self.api.marketplace),
            ("circuits_data", self.api.circuits),
        ]
        if self.capabilities.get("has_stories"):
            jobs.append(("stories_data", self.api.stories))
        if self.capabilities.get("has_partners"):
            jobs.append(("partners_data", self.api.ski_partners))
        if self.capabilities.get("has_instructors"):
            jobs.append(("instructors_data", self.api.instructor_services))
        if self.capabilities.get("has_messages"):
            jobs.append(("messages_data", self.api.messages))

        results = await asyncio.gather(*[job[1]() for job in jobs], return_exceptions=True)

        errors = []
        for (target, _call), result in zip(jobs, results):
            if isinstance(result, Exception):
                setattr(self, target, [])
                errors.append(str(result))
            else:
                setattr(self, target, result)

        self._refresh_summary()
        self._render_all_sections()

        if errors:
            self._set_status("status_error", message=errors[0])
        else:
            self._set_status("status_ready")

    def on_nav_press(self, widget):
        self._show_section(widget.nav_key)

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
            await self._load_capabilities()
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
            await self._load_capabilities()
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
            await self._load_capabilities()
            self._build_app_view()
            await self._load_all_data()
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_google_browser_login(self, widget):
        parts = self.api.swagger_url.split("/swagger/")[0]
        webbrowser.open(f"{parts}/accounts/google/login/?process=login")
        self._set_status("status_ready")

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

    def on_refresh_stories(self, widget):
        asyncio.create_task(self._refresh_stories_only())

    async def _refresh_stories_only(self):
        self._set_status("status_loading")
        try:
            self.stories_data = await self.api.stories()
            self._render_stories_list()
            self._refresh_summary()
            self._set_status("status_ready")
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_refresh_partners(self, widget):
        asyncio.create_task(self._refresh_partners_only())

    async def _refresh_partners_only(self):
        self._set_status("status_loading")
        try:
            self.partners_data = await self.api.ski_partners()
            self._render_partners_list()
            self._refresh_summary()
            self._set_status("status_ready")
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_refresh_instructors(self, widget):
        asyncio.create_task(self._refresh_instructors_only())

    async def _refresh_instructors_only(self):
        self._set_status("status_loading")
        try:
            self.instructors_data = await self.api.instructor_services()
            self._render_instructors_list()
            self._refresh_summary()
            self._set_status("status_ready")
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_refresh_messages(self, widget):
        asyncio.create_task(self._refresh_messages_only())

    async def _refresh_messages_only(self):
        self._set_status("status_loading")
        try:
            self.messages_data = await self.api.messages()
            self._render_messages_list()
            self._refresh_summary()
            self._set_status("status_ready")
        except ApiError as exc:
            self._set_status("status_error", message=str(exc))

    def on_send_message(self, widget):
        asyncio.create_task(self._do_send_message())

    async def _do_send_message(self):
        recipient_raw = (self.msg_recipient_input.value or "").strip()
        subject = (self.msg_subject_input.value or "").strip()
        body = (self.msg_body_input.value or "").strip()

        if not recipient_raw or not subject or not body:
            self._set_status("status_error", message="Missing message fields")
            return

        try:
            recipient = int(recipient_raw)
        except ValueError:
            self._set_status("status_error", message="Recipient ID must be numeric")
            return

        self._set_status("status_loading")
        try:
            await self.api.create_message(recipient=recipient, subject=subject, body=body)
            self.msg_subject_input.value = ""
            self.msg_body_input.value = ""
            await self._refresh_messages_only()
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
        self.capabilities = {}
        self.stations_data = []
        self.bus_data = []
        self.services_data = []
        self.market_data = []
        self.circuits_data = []
        self.messages_data = []
        self.stories_data = []
        self.partners_data = []
        self.instructors_data = []
        self._build_auth_view()
        self._set_status("status_ready")


def main():
    return GrenobleSkiMobile()
