document.addEventListener('DOMContentLoaded', function () {
    const stationsPerPage = 2;  // Define how many stations per page
    const stations = document.querySelectorAll('.station-item');
    const totalPages = Math.ceil(stations.length / stationsPerPage);
    let currentPage = 1;

    // Get references to pagination controls
    const prevBtn = document.getElementById('prev-btn');
    const nextBtn = document.getElementById('next-btn');
    const paginationNumbers = document.getElementById('pagination-numbers');

    // Function to display only the stations for the current page
    function displayStations(page) {
        stations.forEach((station, index) => {
            station.style.display = 'none';  // Hide all stations
        });

        const start = (page - 1) * stationsPerPage;
        const end = start + stationsPerPage;

        stations.forEach((station, index) => {
            if (index >= start && index < end) {
                station.style.display = 'block';  // Show stations for the current page
            }
        });
    }

    // Function to create pagination buttons
    function createPaginationButtons() {
        paginationNumbers.innerHTML = '';  // Clear previous buttons
        for (let i = 1; i <= totalPages; i++) {
            const pageButton = document.createElement('button');
            pageButton.className = 'page-btn';
            pageButton.textContent = i;
            pageButton.addEventListener('click', () => {
                currentPage = i;
                displayStations(currentPage);
                updateButtons();
            });
            paginationNumbers.appendChild(pageButton);
        }
    }

    // Function to update buttons (disable if on the first or last page)
    function updateButtons() {
        if (currentPage === 1) {
            prevBtn.disabled = true;
        } else {
            prevBtn.disabled = false;
        }

        if (currentPage === totalPages) {
            nextBtn.disabled = true;
        } else {
            nextBtn.disabled = false;
        }

        // Highlight the current page button
        const pageButtons = document.querySelectorAll('.page-btn');
        pageButtons.forEach((button, index) => {
            if (index + 1 === currentPage) {
                button.classList.add('active');
            } else {
                button.classList.remove('active');
            }
        });
    }

    // Event listeners for previous and next buttons
    prevBtn.addEventListener('click', () => {
        if (currentPage > 1) {
            currentPage--;
            displayStations(currentPage);
            updateButtons();
        }
    });

    nextBtn.addEventListener('click', () => {
        if (currentPage < totalPages) {
            currentPage++;
            displayStations(currentPage);
            updateButtons();
        }
    });

    // Initialize the pagination
    displayStations(currentPage);
    createPaginationButtons();
    updateButtons();
});