from rest_framework.routers import DefaultRouter
from .views import SkiStationViewSet, BusLineViewSet, ServiceStoreViewSet, SkiCircuitViewSet,SkiMaterialListingViewSet

router = DefaultRouter()
router.register(r'skistations', SkiStationViewSet)
router.register(r'buslines', BusLineViewSet)
router.register(r'servicestores', ServiceStoreViewSet)
router.register(r'skicircuits', SkiCircuitViewSet)
router.register(r'skimaterial', SkiMaterialListingViewSet)

urlpatterns = router.urls
