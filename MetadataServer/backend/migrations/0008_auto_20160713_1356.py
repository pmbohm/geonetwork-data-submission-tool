# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0007_auto_20160712_1431'),
    ]

    operations = [
        migrations.AlterField(
            model_name='institution',
            name='postalCode',
            field=models.CharField(max_length=64, verbose_name=b'postcode'),
        ),
    ]
