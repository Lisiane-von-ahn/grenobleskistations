from django.db import transaction
from api.models import SkiStation, BusLine, ServiceStore, SkiCircuit

@transaction.atomic
def reset_ski_stations():
    # Step 1: Delete all existing ski stations
    SkiStation.objects.all().delete()
    print("All ski stations have been deleted.")

    # Step 2: Add new ski stations around Grenoble
    new_stations = [
        {
            "name": "Chamrousse",
            "latitude": 45.107944,
            "longitude": 5.879200,
            "capacity": 15000,
        },
        {
            "name": "Les 7 Laux",
            "latitude": 45.239000,
            "longitude": 5.976111,
            "capacity": 12000,
        },
        {
            "name": "Alpe d'Huez",
            "latitude": 45.092136,
            "longitude": 6.068349,
            "capacity": 18000,
        },
        {
            "name": "Villard-de-Lans",
            "latitude": 45.070833,
            "longitude": 5.556944,
            "capacity": 14000,
        },
        {
            "name": "Autrans-Méaudre en Vercors",
            "latitude": 45.169722,
            "longitude": 5.527222,
            "capacity": 8000,
        },
    ]

    # Step 3: Bulk create the new ski stations
    ski_station_objects = [
        SkiStation(
            name=station["name"],
            latitude=station["latitude"],
            longitude=station["longitude"],
            capacity=station["capacity"]
        )
        for station in new_stations
    ]
    SkiStation.objects.bulk_create(ski_station_objects)

    print(f"{len(ski_station_objects)} new ski stations have been added.")

# Run the function
if __name__ == '__main__':
    reset_ski_stations()
