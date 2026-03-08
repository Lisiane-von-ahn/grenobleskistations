# Generated manually for marketplace upgrade
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0009_alter_userprofile_user'),
    ]

    operations = [
        migrations.AlterField(
            model_name='skimateriallisting',
            name='material_type',
            field=models.CharField(
                choices=[
                    ('ski', 'Ski'),
                    ('boots', 'Boots'),
                    ('helmet', 'Helmet'),
                    ('jacket', 'Jacket'),
                    ('pants', 'Pants'),
                    ('gloves', 'Gloves'),
                    ('goggles', 'Goggles'),
                    ('other', 'Other'),
                ],
                default='ski',
                max_length=20,
            ),
        ),
        migrations.AddField(
            model_name='skimateriallisting',
            name='brand',
            field=models.CharField(blank=True, default='', max_length=100),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='skimateriallisting',
            name='condition',
            field=models.CharField(
                choices=[
                    ('new', 'New'),
                    ('excellent', 'Excellent'),
                    ('good', 'Good'),
                    ('fair', 'Fair'),
                ],
                default='good',
                max_length=12,
            ),
        ),
        migrations.AddField(
            model_name='skimateriallisting',
            name='size',
            field=models.CharField(blank=True, default='', max_length=30),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='skimateriallisting',
            name='transaction_type',
            field=models.CharField(
                choices=[('sale', 'For Sale'), ('rent', 'For Rent'), ('lend', 'For Loan')],
                default='sale',
                max_length=10,
            ),
        ),
        migrations.CreateModel(
            name='SkiMaterialImage',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('image', models.BinaryField()),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                (
                    'listing',
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name='images',
                        to='api.skimateriallisting',
                    ),
                ),
            ],
            options={'ordering': ['created_at']},
        ),
    ]
