from django.test import TestCase
from django.contrib.auth.models import User
from django.urls import reverse
from unittest.mock import patch

from rest_framework.authtoken.models import Token
from rest_framework.test import APIClient

from .models import SkiMaterialListing, Message


class MarketplaceFlowTests(TestCase):
	def setUp(self):
		self.seller = User.objects.create_user(username='seller', password='pwd12345')
		self.buyer = User.objects.create_user(username='buyer', password='pwd12345')
		self.listing = SkiMaterialListing.objects.create(
			user=self.seller,
			title='Skis all mountain',
			description='Très bon état',
			city='Grenoble',
			price=150,
			material_type='ski',
			transaction_type='sale',
			condition='good',
			brand='Rossignol',
			size='170',
		)

	def test_marketplace_search_filters_by_city(self):
		self.client.login(username='buyer', password='pwd12345')
		response = self.client.get(reverse('ski_material_listings'), {'city': 'Grenoble'})
		self.assertContains(response, 'Skis all mountain')

	def test_contact_seller_from_listing_detail(self):
		self.client.login(username='buyer', password='pwd12345')
		response = self.client.post(
			reverse('listing_detail', kwargs={'id': self.listing.id}),
			{'body': 'Bonjour, est-ce disponible ?'},
			follow=True,
		)
		self.assertEqual(response.status_code, 200)
		self.assertEqual(Message.objects.count(), 1)
		msg = Message.objects.first()
		self.assertEqual(msg.sender, self.buyer)
		self.assertEqual(msg.recipient, self.seller)


class ApiAuthTests(TestCase):
	def setUp(self):
		self.client = APIClient()

	def test_register_and_me_flow(self):
		register_response = self.client.post(
			'/api/auth/register/',
			{
				'email': 'newuser@example.com',
				'password': 'StrongPass123!',
				'first_name': 'New',
				'last_name': 'User',
			},
			format='json',
		)
		self.assertEqual(register_response.status_code, 201)
		token = register_response.data.get('token')
		self.assertTrue(token)

		self.client.credentials(HTTP_AUTHORIZATION=f'Token {token}')
		me_response = self.client.get('/api/auth/me/')
		self.assertEqual(me_response.status_code, 200)
		self.assertEqual(me_response.data['user']['email'], 'newuser@example.com')

	def test_login_returns_token_and_user(self):
		user = User.objects.create_user(
			username='existing@example.com',
			email='existing@example.com',
			password='StrongPass123!',
		)
		response = self.client.post(
			'/api/auth/login/',
			{'email': 'existing@example.com', 'password': 'StrongPass123!'},
			format='json',
		)
		self.assertEqual(response.status_code, 200)
		self.assertEqual(response.data['user']['id'], user.id)
		self.assertTrue(Token.objects.filter(user=user).exists())

	@patch('api.views._verify_google_token', side_effect=Exception('invalid token'))
	def test_google_login_invalid_token(self, _mock_verify):
		response = self.client.post(
			'/api/auth/google-login/',
			{'id_token': 'bad-token'},
			format='json',
		)
		self.assertEqual(response.status_code, 400)
		self.assertIn('error', response.data)

	@patch('api.views._verify_google_token')
	def test_google_login_creates_user(self, mock_verify):
		mock_verify.return_value = {
			'email': 'googleuser@example.com',
			'email_verified': True,
			'given_name': 'Google',
			'family_name': 'User',
		}

		response = self.client.post(
			'/api/auth/google-login/',
			{'id_token': 'good-token'},
			format='json',
		)
		self.assertEqual(response.status_code, 200)
		self.assertTrue(response.data.get('token'))
		self.assertEqual(response.data['user']['email'], 'googleuser@example.com')
		self.assertTrue(User.objects.filter(email='googleuser@example.com').exists())
