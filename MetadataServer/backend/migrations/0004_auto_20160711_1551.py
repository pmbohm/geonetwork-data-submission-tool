# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0003_parameterinstrument_parametername_parameterplatform_parameterunit'),
    ]

    operations = [
        migrations.AddField(
            model_name='parameterinstrument',
            name='Version',
            field=models.CharField(default='version-1-0', max_length=16),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parametername',
            name='Version',
            field=models.CharField(default='version-1-0', max_length=16),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterplatform',
            name='Version',
            field=models.CharField(default='version-1-0', max_length=16),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterunit',
            name='Version',
            field=models.CharField(default='version-1-0', max_length=16),
            preserve_default=False,
        ),
    ]
