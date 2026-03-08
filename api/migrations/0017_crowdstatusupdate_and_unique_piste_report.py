from django.db import migrations, models
import django.db.models.deletion


def deduplicate_piste_reports(apps, schema_editor):
    PisteConditionReport = apps.get_model('api', 'PisteConditionReport')
    seen = set()
    duplicates = []

    reports = PisteConditionReport.objects.all().order_by('ski_station_id', 'user_id', '-created_at', '-id')
    for report in reports:
        key = (report.ski_station_id, report.user_id)
        if key in seen:
            duplicates.append(report.id)
        else:
            seen.add(key)

    if duplicates:
        PisteConditionReport.objects.filter(id__in=duplicates).delete()


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0016_servicestore_address_website_url'),
    ]

    operations = [
        migrations.CreateModel(
            name='CrowdStatusUpdate',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('crowd_level', models.CharField(choices=[('quiet', 'Peu de gens'), ('normal', 'Agréable'), ('busy', 'Bondé')], default='normal', max_length=10)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('ski_station', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='crowd_updates', to='api.skistation')),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='crowd_updates', to='auth.user')),
            ],
        ),
        migrations.RunPython(deduplicate_piste_reports, migrations.RunPython.noop),
        migrations.AlterUniqueTogether(
            name='pisteconditionreport',
            unique_together={('ski_station', 'user')},
        ),
    ]
