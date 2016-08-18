# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


def setup_datafeeds(apps, schema_editor):
    DataFeed = apps.get_model("backend", "DataFeed")
    DataFeed.objects.create(name="load_rolecodes")

def teardown_datafeeds(apps, schema_editor):
    pass
    
class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0011_auto_20160721_1750'),
    ]

    operations = [
        migrations.RunPython(setup_datafeeds, teardown_datafeeds),
    ]
