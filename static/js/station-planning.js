(() => {
    const grid = document.getElementById('planning-grid');
    const cards = [...grid.children];
    const search = document.getElementById('station-filter');
    const open = document.getElementById('open-filter');
    const bus = document.getElementById('bus-filter');
    const favorites = document.getElementById('favorite-filter');
    const beginner = document.getElementById('beginner-filter');
    const budget = document.getElementById('budget-filter');
    const sort = document.getElementById('planning-sort');
    function price(card) {
        const match = card.dataset.price.match(/[0-9]+(?:,[0-9]+)?/);
        return match ? Number(match[0].replace(',', '.')) : Infinity;
    }
    function update() {
        cards.sort((a, b) => {
            if (sort.value === 'price') return price(a) - price(b);
            if (sort.value === 'snow') return Number(b.dataset.snow || -1) - Number(a.dataset.snow || -1);
            return Number(a.dataset.distance) - Number(b.dataset.distance);
        });
        let visible = 0;
        cards.forEach(card => {
            card.hidden = !card.dataset.name.includes(search.value.toLocaleLowerCase()) ||
                (open.checked && card.dataset.open !== 'open') ||
                (bus.checked && card.dataset.bus !== '1') ||
                (beginner.checked && Number(card.dataset.beginner) <= 0) ||
                (favorites.checked && card.dataset.favorite !== '1') ||
                (budget.value !== '' && price(card) > Number(budget.value));
            if (!card.hidden) visible++;
            grid.appendChild(card);
        });
        document.getElementById('planning-count').textContent = String(visible);
    }
    [search, open, bus, favorites, sort, budget, beginner].forEach(input => input.addEventListener('input', update));
    update();
})();
