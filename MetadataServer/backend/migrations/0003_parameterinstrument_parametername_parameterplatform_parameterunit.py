# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0002_draftmetadata_notefordatamanager'),
    ]

    operations = [
        migrations.CreateModel(
            name='ParameterInstrument',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('URI', models.CharField(max_length=128)),
                ('Name', models.CharField(max_length=128)),
                ('Definition', models.CharField(max_length=2000)),
            ],
            options={
                'ordering': ['Name'],
            },
        ),
        migrations.CreateModel(
            name='ParameterName',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('URI', models.CharField(max_length=128)),
                ('Name', models.CharField(max_length=128)),
                ('Definition', models.CharField(max_length=512)),
            ],
            options={
                'ordering': ['Name'],
            },
        ),
        migrations.CreateModel(
            name='ParameterPlatform',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('URI', models.CharField(max_length=128)),
                ('Name', models.CharField(max_length=128)),
                ('Definition', models.CharField(max_length=2500)),
            ],
            options={
                'ordering': ['Name'],
            },
        ),
        migrations.CreateModel(
            name='ParameterUnit',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('URI', models.CharField(max_length=128)),
                ('Name', models.CharField(max_length=128)),
                ('Definition', models.CharField(max_length=128)),
            ],
            options={
                'ordering': ['Name'],
            },
        ),
    ]
