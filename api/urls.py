from rest_framework.routers import DefaultRouter
from .views import SkiStationViewSet, BusLineViewSet, ServiceStoreViewSet, SkiCircuitViewSet

router = DefaultRouter()
router.register(r'skistations', SkiStationViewSet)
router.register(r'buslines', BusLineViewSet)
router.register(r'servicestores', ServiceStoreViewSet)
router.register(r'skicircuits', SkiCircuitViewSet)

urlpatterns = router.urls
