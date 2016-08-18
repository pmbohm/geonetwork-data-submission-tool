# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models
import django_fsm


class Migration(migrations.Migration):

    dependencies = [
        ('backend', '0004_auto_20160711_1551'),
    ]

    operations = [
        migrations.CreateModel(
            name='DataFeed',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('name', models.SlugField()),
                ('state', django_fsm.FSMField(default=b'Scheduled', max_length=50, choices=[(b'Idle', b'Idle'), (b'Scheduled', b'Scheduled'), (b'Active', b'Active')])),
                ('last_refresh', models.DateTimeField(null=True, blank=True)),
                ('last_success', models.DateTimeField(null=True, blank=True)),
                ('last_failure', models.DateTimeField(null=True, blank=True)),
                ('last_duration', models.DurationField(null=True, blank=True)),
                ('last_output', models.TextField(blank=True)),
            ],
            options={
                'permissions': (('datafeed_schedule', 'Can schedule datafeed refresh'), ('datafeed_unschedule', 'Can cancel scheduled datafeed schedule'), ('datafeed_admin', 'Can administer datafeed')),
            },
        ),
    ]
