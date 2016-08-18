# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models
import uuid


class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0010_auto_20160713_1610'),
    ]

    operations = [
        migrations.CreateModel(
            name='RoleCode',
            fields=[
                ('UUID', models.UUIDField(default=uuid.uuid4, serialize=False, editable=False, primary_key=True)),
                ('Identifier', models.CharField(max_length=128)),
                ('Description', models.CharField(max_length=128)),
            ],
            options={
                'ordering': ['Identifier', 'Description'],
            },
        ),
        migrations.AlterField(
            model_name='parameterinstrument',
            name='Definition',
            field=models.CharField(max_length=2500, db_column=b'Definition'),
        ),
        migrations.AlterField(
            model_name='parameterinstrument',
            name='Name',
            field=models.CharField(max_length=128, db_column=b'Name'),
        ),
        migrations.AlterField(
            model_name='parameterinstrument',
            name='URI',
            field=models.CharField(max_length=128, db_column=b'URI'),
        ),
        migrations.AlterField(
            model_name='parameterinstrument',
            name='Version',
            field=models.CharField(max_length=16, db_column=b'Version'),
        ),
        migrations.AlterField(
            model_name='parametername',
            name='Definition',
            field=models.CharField(max_length=1024, db_column=b'Definition'),
        ),
        migrations.AlterField(
            model_name='parametername',
            name='Name',
            field=models.CharField(max_length=128, db_column=b'Name'),
        ),
        migrations.AlterField(
            model_name='parametername',
            name='URI',
            field=models.CharField(max_length=128, db_column=b'URI'),
        ),
        migrations.AlterField(
            model_name='parametername',
            name='Version',
            field=models.CharField(max_length=16, db_column=b'Version'),
        ),
        migrations.AlterField(
            model_name='parameterplatform',
            name='Definition',
            field=models.CharField(max_length=5000, db_column=b'Definition'),
        ),
        migrations.AlterField(
            model_name='parameterplatform',
            name='Name',
            field=models.CharField(max_length=128, db_column=b'Name'),
        ),
        migrations.AlterField(
            model_name='parameterplatform',
            name='URI',
            field=models.CharField(max_length=128, db_column=b'URI'),
        ),
        migrations.AlterField(
            model_name='parameterplatform',
            name='Version',
            field=models.CharField(max_length=16, db_column=b'Version'),
        ),
        migrations.AlterField(
            model_name='parameterunit',
            name='Definition',
            field=models.CharField(max_length=256, db_column=b'Definition'),
        ),
        migrations.AlterField(
            model_name='parameterunit',
            name='Name',
            field=models.CharField(max_length=128, db_column=b'Name'),
        ),
        migrations.AlterField(
            model_name='parameterunit',
            name='URI',
            field=models.CharField(max_length=128, db_column=b'URI'),
        ),
        migrations.AlterField(
            model_name='parameterunit',
            name='Version',
            field=models.CharField(max_length=16, db_column=b'Version'),
        ),
    ]
