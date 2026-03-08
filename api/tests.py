from django.test import TestCase
from django.contrib.auth.models import User
from django.urls import reverse

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
