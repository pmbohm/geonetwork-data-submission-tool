from django.conf.urls import patterns, include, url
from rest_framework import routers

from frontend.views import *

import backend.viewsets as viewsets


router = routers.DefaultRouter()
router.register(r'institution', viewsets.InstitutionViewSet)
router.register(r'sciencekeyword', viewsets.ScienceKeywordViewSet)
router.register(r'rolecode', viewsets.RoleCodeViewSet)
router.register(r'parametername', viewsets.ParameterNameViewSet)
router.register(r'parameterunit', viewsets.ParameterUnitViewSet)
router.register(r'parameterinstrument', viewsets.ParameterInstrumentViewSet)
router.register(r'parameterplatform', viewsets.ParameterPlatformViewSet)

urlpatterns = patterns(
    '',
    url(r'^$', home, name="LandingPage"),
    url(r'^dashboard/$', dashboard, name="Dashboard"),
    url(r'^edit/(?P<uuid>\w{8}-\w{4}-\w{4}-\w{4}-\w{12})/$', edit, name="Edit"),
    url(r'^save/(?P<uuid>\w{8}-\w{4}-\w{4}-\w{4}-\w{12})/$', save, name="Save"),
    url(r'^transition/(?P<uuid>\w{8}-\w{4}-\w{4}-\w{4}-\w{12})/$', transition, name="Transition"),
    url(r'^clone/(?P<uuid>\w{8}-\w{4}-\w{4}-\w{4}-\w{12})/$', clone, name="Clone"),
    url(r'^upload/(?P<uuid>\w{8}-\w{4}-\w{4}-\w{4}-\w{12})/$', UploadView.as_view(), name="Upload"),
    url(r'^delete/(?P<uuid>\w{8}-\w{4}-\w{4}-\w{4}-\w{12})/(?P<id>\d+)/$', delete_attachment,
        name="DeleteAttachment"),
    url(r'^create/$', create, name="Create"),
    url(r'^theme/$', theme, name="Theme"),
    url(r'^export/(?P<uuid>\w{8}-\w{4}-\w{4}-\w{4}-\w{12})/$', export, name="Export"),
    url(r'^mef/(?P<uuid>\w{8}-\w{4}-\w{4}-\w{4}-\w{12})/$', mef, name="MEF"),
    url(r'^api/', include(router.urls)),
    url(r'^api-auth/', include('rest_framework.urls', namespace='rest_framework')),
)
