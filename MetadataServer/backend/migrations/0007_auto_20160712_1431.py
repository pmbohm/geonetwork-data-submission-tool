# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0006_auto_20160712_1206'),
    ]

    operations = [
        migrations.AddField(
            model_name='parameterinstrument',
            name='depth',
            field=models.PositiveIntegerField(default=0, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterinstrument',
            name='lft',
            field=models.PositiveIntegerField(default=0, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterinstrument',
            name='rgt',
            field=models.PositiveIntegerField(default=1, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterinstrument',
            name='tree_id',
            field=models.PositiveIntegerField(default=0, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parametername',
            name='depth',
            field=models.PositiveIntegerField(default=0, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parametername',
            name='lft',
            field=models.PositiveIntegerField(default=1, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parametername',
            name='rgt',
            field=models.PositiveIntegerField(default=1, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parametername',
            name='tree_id',
            field=models.PositiveIntegerField(default=1, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterplatform',
            name='depth',
            field=models.PositiveIntegerField(default=0, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterplatform',
            name='lft',
            field=models.PositiveIntegerField(default=0, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterplatform',
            name='rgt',
            field=models.PositiveIntegerField(default=1, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterplatform',
            name='tree_id',
            field=models.PositiveIntegerField(default=1, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterunit',
            name='depth',
            field=models.PositiveIntegerField(default=0, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterunit',
            name='lft',
            field=models.PositiveIntegerField(default=0, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterunit',
            name='rgt',
            field=models.PositiveIntegerField(default=1, db_index=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='parameterunit',
            name='tree_id',
            field=models.PositiveIntegerField(default=1, db_index=True),
            preserve_default=False,
        ),
    ]
