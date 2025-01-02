from social_core.exceptions import SocialAuthBaseException

def set_username_from_email(backend, user, response, *args, **kwargs):
    """
    Set the username as the email address when registering via Google.
    """
    if not user.username:  # Only change if username is not already set
        try:
            email = response.get('email')
            if email:
                user.username = email
                user.save()
        except SocialAuthBaseException:
            pass
