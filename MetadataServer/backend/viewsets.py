from rest_framework import viewsets, filters
from frontend.filters import ParentFilter
import backend.models as models
import backend.serializers as serializers
import django_filters


class InstitutionViewSet(viewsets.ModelViewSet):
    queryset = models.Institution.objects.all()
    serializer_class = serializers.InstitutionSerializer

    search_fields = ('organisationName', 'deliveryPoint', 'deliveryPoint2',
                     'city', 'administrativeArea', 'postalCode', 'country')


class ScienceKeywordViewSet(viewsets.ModelViewSet):
    queryset = models.ScienceKeyword.objects.all()
    serializer_class = serializers.ScienceKeywordSerializer

    search_fields = ('Category', 'Topic', 'Term', 'VariableLevel1',
                     'VariableLevel2', 'VariableLevel3', 'DetailedVariable')


class RoleCodeViewSet(viewsets.ModelViewSet):
    queryset = models.RoleCode.objects.all()
    serializer_class = serializers.RoleCodeSerializer

    search_fields = ('Identifier', 'Description')


class ParameterNameNodeFilter(django_filters.FilterSet):
    min_lft = django_filters.NumberFilter(name="lft", lookup_type='gte')
    max_rgt = django_filters.NumberFilter(name="rgt", lookup_type='lte')
    class Meta:
        model = models.ParameterName
        fields = ['depth', 'tree_id', 'min_lft', 'max_rgt']


class ParameterNameViewSet(viewsets.ModelViewSet):
    queryset = models.ParameterName.objects.all()
    serializer_class = serializers.ParameterNameSerializer
    filter_backends = (filters.SearchFilter, filters.DjangoFilterBackend, ParentFilter)
    filter_class = ParameterNameNodeFilter
    search_fields = ('Name', 'Definition')


class ParameterUnitNodeFilter(django_filters.FilterSet):
    min_lft = django_filters.NumberFilter(name="lft", lookup_type='gte')
    max_rgt = django_filters.NumberFilter(name="rgt", lookup_type='lte')
    min_depth = django_filters.NumberFilter(name="depth", lookup_type='gte')
    max_depth = django_filters.NumberFilter(name="depth", lookup_type='lte')
    class Meta:
        model = models.ParameterUnit
        fields = ['depth', 'tree_id', 'min_lft', 'max_rgt', 'min_depth', 'max_depth']


class ParameterUnitViewSet(viewsets.ModelViewSet):
    queryset = models.ParameterUnit.objects.all()
    serializer_class = serializers.ParameterUnitSerializer
    filter_backends = (filters.SearchFilter, filters.DjangoFilterBackend, filters.OrderingFilter, ParentFilter)
    filter_class = ParameterUnitNodeFilter
    search_fields = ('Name', 'Definition')
    ordering_fields = ('tree_id', 'Name')


class ParameterInstrumentNodeFilter(django_filters.FilterSet):
    min_lft = django_filters.NumberFilter(name="lft", lookup_type='gte')
    max_rgt = django_filters.NumberFilter(name="rgt", lookup_type='lte')
    class Meta:
        model = models.ParameterInstrument
        fields = ['depth', 'tree_id', 'min_lft', 'max_rgt']


class ParameterInstrumentViewSet(viewsets.ModelViewSet):
    queryset = models.ParameterInstrument.objects.all()
    serializer_class = serializers.ParameterInstrumentSerializer
    filter_backends = (filters.SearchFilter, filters.DjangoFilterBackend, ParentFilter)
    filter_class = ParameterInstrumentNodeFilter
    search_fields = ('Name', 'Definition')



class ParameterPlatformNodeFilter(django_filters.FilterSet):
    min_lft = django_filters.NumberFilter(name="lft", lookup_type='gte')
    max_rgt = django_filters.NumberFilter(name="rgt", lookup_type='lte')
    class Meta:
        model = models.ParameterPlatform
        fields = ['depth', 'tree_id', 'min_lft', 'max_rgt']


class ParameterPlatformViewSet(viewsets.ModelViewSet):
    queryset = models.ParameterPlatform.objects.all()
    serializer_class = serializers.ParameterPlatformSerializer
    filter_backends = (filters.SearchFilter, filters.DjangoFilterBackend, ParentFilter)
    filter_class = ParameterPlatformNodeFilter
    search_fields = ('Name', 'Definition')
