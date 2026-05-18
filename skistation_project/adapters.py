import logging

from allauth.account.adapter import DefaultAccountAdapter
from allauth.socialaccount.adapter import DefaultSocialAccountAdapter
from django.utils import translation


logger = logging.getLogger('skistation.adapters')

    
class CustomAccountAdapter(DefaultAccountAdapter):
    def save_user(self, request, user, form, commit=True):
        user = super().save_user(request, user, form, commit=False)
        user.email = (user.email or '').strip().lower()
        user.username = user.email
        user.first_name = (getattr(user, 'first_name', '') or '').strip()
        user.last_name = (getattr(user, 'last_name', '') or '').strip()
        if commit:
            user.save()
        logger.info("Saving account user with email-as-username user_email=%s", user.email)
        return user

    def send_mail(self, template_prefix, email, context):
        request = context.get('request')
        language_code = translation.get_language()
        if request is not None:
            language_code = (
                getattr(request, 'LANGUAGE_CODE', None)
                or request.session.get('django_language')
                or request.COOKIES.get('django_language')
                or language_code
            )

        if language_code:
            with translation.override(language_code):
                return super().send_mail(template_prefix, email, context)
        return super().send_mail(template_prefix, email, context)

    def get_email_verification_redirect_url(self, email_address):
        # Keep BI login separate: always redirect to the tenant login page after email verification.
        return 'https://audeladedonnees.fr/tenant/login'
    

class CustomSocialAccountAdapter(DefaultSocialAccountAdapter):
    def populate_user(self, request, sociallogin, data):
        user = super().populate_user(request, sociallogin, data)
        email = (data.get('email') or user.email or '').strip().lower()
        first_name = (data.get('given_name') or data.get('first_name') or user.first_name or '').strip()
        last_name = (data.get('family_name') or data.get('last_name') or user.last_name or '').strip()

        if email:
            user.email = email
            user.username = email
        user.first_name = first_name
        user.last_name = last_name
        return user

    def save_user(self, request, sociallogin, form=None):
        user = sociallogin.user
        if user.email:
            user.email = user.email.strip().lower()
            user.username = user.email
        logger.info("Saving social account user with email-as-username user_email=%s", user.email)
        return super().save_user(request, sociallogin, form)