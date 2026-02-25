import logging

from allauth.account.adapter import DefaultAccountAdapter
from allauth.socialaccount.adapter import DefaultSocialAccountAdapter


logger = logging.getLogger('skistation.adapters')

    
class CustomAccountAdapter(DefaultAccountAdapter):
    def save_user(self, request, user, form, commit=True):
        logger.info("Saving account user with email-as-username user_email=%s", user.email)
        user.username = user.email
        return super().save_user(request, user, form, commit)
    

class CustomSocialAccountAdapter(DefaultSocialAccountAdapter):

    def save_user(self, request, sociallogin, form=None):
        user = sociallogin.user
        logger.info("Saving social account user with email-as-username user_email=%s", user.email)
        user.username = user.email

        user.save()

        return super().save_user(request, sociallogin, form)