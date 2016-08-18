# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0012_rolecodes'),
    ]

    operations = [
        migrations.AddField(
            model_name='institution',
            name='altLabel',
            field=models.CharField(default=b'', max_length=512),
        ),
        migrations.AddField(
            model_name='institution',
            name='exactMatch',
            field=models.CharField(default=b'', max_length=512),
        ),
        migrations.AddField(
            model_name='institution',
            name='prefLabel',
            field=models.CharField(default=b'', max_length=512),
        ),
        migrations.AddField(
            model_name='institution',
            name='uri',
            field=models.CharField(default=b'', max_length=512),
        ),
    ]
