function skiStationMapIcon() {
    return L.divIcon({
        className: 'ski-map-pin',
        html: '<span aria-hidden="true"></span>',
        iconSize: [38, 42],
        iconAnchor: [19, 41],
        popupAnchor: [0, -38],
    });
}
