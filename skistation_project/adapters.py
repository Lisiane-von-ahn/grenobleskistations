import logging

from allauth.account.adapter import DefaultAccountAdapter
from allauth.socialaccount.adapter import DefaultSocialAccountAdapter


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