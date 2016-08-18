# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


def setup_datafeeds(apps, schema_editor):
    DataFeed = apps.get_model("backend", "DataFeed")
    DataFeed.objects.create(name="load_institutions")
    DataFeed.objects.create(name="load_parameterinstruments")
    DataFeed.objects.create(name="load_parameternames")
    DataFeed.objects.create(name="load_parameterplatforms")
    DataFeed.objects.create(name="load_parameterunits")

def teardown_datafeeds(apps, schema_editor):
    pass

class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0005_datafeed'),
    ]

    operations = [
        migrations.RunPython(setup_datafeeds, teardown_datafeeds),
    ]
