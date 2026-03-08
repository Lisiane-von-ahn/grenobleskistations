from django.apps import AppConfig


class SkistationProjectConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'skistation_project'

    def ready(self):
        from . import signals  # noqa: F401
