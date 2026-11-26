const bottomDock = document.querySelector('[data-bottom-dock]');
if (bottomDock) {
    const reserveDockSpace = () => {
        document.documentElement.style.setProperty('--bottom-dock-height', `${bottomDock.offsetHeight}px`);
    };

    reserveDockSpace();
    const dockObserver = new ResizeObserver(reserveDockSpace);
    dockObserver.observe(bottomDock);
}
