from allauth.account.adapter import DefaultAccountAdapter
from allauth.socialaccount.adapter import DefaultSocialAccountAdapter

    
class CustomAccountAdapter(DefaultAccountAdapter):
    def save_user(self, request, user, form, commit=True):
        # Set the username to the email
        print("teste");
        user.username = user.email
        return super().save_user(request, user, form, commit)
    

class CustomSocialAccountAdapter(DefaultSocialAccountAdapter):

    def save_user(self, request, sociallogin, form=None):
        user = sociallogin.user
        user.username = user.email

        user.save()

        return super().save_user(request, sociallogin, form)