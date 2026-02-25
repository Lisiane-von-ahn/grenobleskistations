from django.contrib.auth.backends import ModelBackend
from django.contrib.auth import get_user_model


class EmailOrUsernameModelBackend(ModelBackend):
    def authenticate(self, request, username=None, password=None, **kwargs):
        if username is None:
            username = kwargs.get(get_user_model().USERNAME_FIELD)

        if username is None or password is None:
            return None

        user_model = get_user_model()
        user = None

        if '@' in username:
            user = user_model.objects.filter(email__iexact=username).first()

        if user is None:
            user = user_model.objects.filter(username__iexact=username).first()

        if user and user.check_password(password) and self.user_can_authenticate(user):
            return user

        return None
