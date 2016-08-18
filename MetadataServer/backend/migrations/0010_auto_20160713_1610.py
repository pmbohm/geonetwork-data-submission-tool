# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0009_auto_20160713_1415'),
    ]

    operations = [
        migrations.AddField(
            model_name='parameterinstrument',
            name='is_selectable',
            field=models.BooleanField(default=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parametername',
            name='is_selectable',
            field=models.BooleanField(default=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterplatform',
            name='is_selectable',
            field=models.BooleanField(default=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterunit',
            name='is_selectable',
            field=models.BooleanField(default=True),
            preserve_default=False,
        ),
    ]
