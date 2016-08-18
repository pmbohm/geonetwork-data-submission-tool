# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0008_auto_20160713_1356'),
    ]

    operations = [
        migrations.AlterField(
            model_name='parameterunit',
            name='Definition',
            field=models.CharField(max_length=256),
        ),
    ]
