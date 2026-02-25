import logging

from django.contrib.auth.signals import user_logged_in, user_logged_out, user_login_failed
from django.dispatch import receiver
from allauth.account.signals import password_changed

from api.models import UserProfile


auth_logger = logging.getLogger('skistation.auth')


def _client_ip(request):
    if request is None:
        return ''
    forwarded = request.META.get('HTTP_X_FORWARDED_FOR', '')
    if forwarded:
        return forwarded.split(',')[0].strip()
    return request.META.get('REMOTE_ADDR', '')


@receiver(user_logged_in)
def on_user_logged_in(sender, request, user, **kwargs):
    auth_logger.info(
        'User logged in user_id=%s username=%s path=%s ip=%s',
        user.id,
        user.get_username(),
        request.path if request else '',
        _client_ip(request),
    )


@receiver(user_logged_out)
def on_user_logged_out(sender, request, user, **kwargs):
    auth_logger.info(
        'User logged out user_id=%s username=%s path=%s ip=%s',
        getattr(user, 'id', 'anonymous'),
        user.get_username() if user else 'anonymous',
        request.path if request else '',
        _client_ip(request),
    )


@receiver(user_login_failed)
def on_user_login_failed(sender, credentials, request, **kwargs):
    identifier = (
        credentials.get('username')
        or credentials.get('email')
        or credentials.get('login')
        or ''
    )
    auth_logger.warning(
        'User login failed identifier=%s path=%s ip=%s',
        identifier,
        request.path if request else '',
        _client_ip(request),
    )


@receiver(password_changed)
def on_password_changed(request, user, **kwargs):
    profile, _ = UserProfile.objects.get_or_create(user=user)
    if profile.force_password_reset:
        profile.force_password_reset = False
        profile.save(update_fields=['force_password_reset'])
        auth_logger.info('Password reset flag cleared user_id=%s username=%s', user.id, user.get_username())
