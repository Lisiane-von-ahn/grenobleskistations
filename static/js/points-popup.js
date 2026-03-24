/**
 * Points Earned Popup System
 * Displays beautiful modal dialogs when user earns points
 */

class PointsEarnedPopup {
    constructor() {
        this.createModalHTML();
    }

    createModalHTML() {
        const modalHTML = `
            <div id="pointsEarnedModal" class="modal fade" tabindex="-1" role="dialog">
                <div class="modal-dialog modal-dialog-centered" role="document">
                    <div class="modal-content border-0 shadow-lg">
                        <!-- Header with celebration animation -->
                        <div class="modal-header bg-gradient-success border-0 position-relative overflow-hidden">
                            <div class="confetti-container"></div>
                            <h5 class="modal-title w-100 text-center text-white font-weight-bold" style="font-size: 1.5rem;">
                                🎉 Points Earned!
                            </h5>
                            <button type="button" class="close text-white" data-dismiss="modal" aria-label="Close">
                                <span aria-hidden="true">&times;</span>
                            </button>
                        </div>

                        <!-- Body with point details -->
                        <div class="modal-body text-center py-4">
                            <!-- Big Points Number -->
                            <div class="mb-3">
                                <div class="display-1 text-success font-weight-bold" id="popupPoints" style="animation: popIn 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);">
                                    +15
                                </div>
                                <p class="text-muted" id="popupActivityType" style="font-size: 1.1rem;">
                                    Added Material Listing
                                </p>
                            </div>

                            <!-- Level and XP Progress -->
                            <div class="card bg-light border-0 mb-3">
                                <div class="card-body py-3">
                                    <div class="row text-center">
                                        <div class="col-6">
                                            <p class="text-muted small mb-1">YOUR LEVEL</p>
                                            <h3 class="mb-0" id="popupLevel">8</h3>
                                        </div>
                                        <div class="col-6">
                                            <p class="text-muted small mb-1">TOTAL POINTS</p>
                                            <h3 class="mb-0" id="popupTotalPoints">2,450</h3>
                                        </div>
                                    </div>

                                    <!-- XP Progress Bar -->
                                    <div class="mt-3">
                                        <div class="progress" style="height: 10px; border-radius: 10px;">
                                            <div id="popupXpBar" class="progress-bar bg-success" role="progressbar" 
                                                 style="width: 75%; border-radius: 10px;"></div>
                                        </div>
                                        <small class="text-muted mt-2 d-block" id="popupXpText">
                                            150 / 300 XP to Level 9
                                        </small>
                                    </div>
                                </div>
                            </div>

                            <!-- Badge Unlock (if applicable) -->
                            <div id="badgeUnlockContainer" class="badge-unlock-animation mb-3" style="display: none;">
                                <div class="alert alert-info border-2 border-info" role="alert">
                                    <h5 class="alert-heading mb-2">
                                        🏆 Badge Unlocked!
                                    </h5>
                                    <div class="badge badge-lg" id="popupBadgeEmoji" style="font-size: 2rem; margin-bottom: 10px;">
                                        🏪
                                    </div>
                                    <p class="mb-1" id="popupBadgeName">
                                        <strong>Marketplace Seller</strong>
                                    </p>
                                    <small id="popupBadgeDesc">
                                        You've created your first listing!
                                    </small>
                                </div>
                            </div>

                            <!-- Stats Summary -->
                            <div class="row text-center small text-muted mt-3">
                                <div class="col-4">
                                    <p class="mb-1">Listings</p>
                                    <h5 id="popupListings" class="text-dark">7</h5>
                                </div>
                                <div class="col-4">
                                    <p class="mb-1">Deals</p>
                                    <h5 id="popupDeals" class="text-dark">4</h5>
                                </div>
                                <div class="col-4">
                                    <p class="mb-1">Rating</p>
                                    <h5 id="popupRating" class="text-dark">⭐ 4.8</h5>
                                </div>
                            </div>
                        </div>

                        <!-- Footer with action button -->
                        <div class="modal-footer bg-light border-top-0">
                            <button type="button" class="btn btn-success w-100" data-dismiss="modal">
                                Awesome! 🚀
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <style>
                @keyframes popIn {
                    0% {
                        transform: scale(0) rotateZ(-10deg);
                        opacity: 0;
                    }
                    50% {
                        transform: scale(1.15) rotateZ(5deg);
                    }
                    100% {
                        transform: scale(1) rotateZ(0deg);
                        opacity: 1;
                    }
                }

                @keyframes confetti-fall {
                    to {
                        transform: translateY(100px) rotateZ(360deg);
                        opacity: 0;
                    }
                }

                @keyframes slideInDown {
                    from {
                        transform: translateY(-200px);
                        opacity: 0;
                    }
                    to {
                        transform: translateY(0);
                        opacity: 1;
                    }
                }

                .modal-content {
                    border-radius: 15px;
                    animation: slideInDown 0.4s ease-out;
                }

                .bg-gradient-success {
                    background: linear-gradient(135deg, #27ae60 0%, #2ecc71 100%) !important;
                }

                .badge-lg {
                    display: inline-block;
                    font-size: 3rem;
                    animation: popIn 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) 0.3s backwards;
                }

                .confetti-container {
                    position: absolute;
                    width: 100%;
                    height: 100%;
                    top: 0;
                    left: 0;
                    pointer-events: none;
                }

                .confetti {
                    position: absolute;
                    width: 10px;
                    height: 10px;
                    background: #2ecc71;
                    animation: confetti-fall 2s ease-in forwards;
                }

                .modal-header .close {
                    opacity: 0.7;
                    transition: opacity 0.2s;
                }

                .modal-header .close:hover {
                    opacity: 1;
                }
            </style>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHTML);
    }

    /**
     * Show points earned popup
     * @param {Object} data - Point data from API
     * @param {number} data.points_earned - Points earned
     * @param {string} data.activity_type - Activity type
     * @param {Object} data.stats - User stats (level, total_points, etc)
     * @param {Object} data.badge - Badge info if unlocked (optional)
     */
    show(data) {
        const modal = document.getElementById('pointsEarnedModal');
        
        // Update content
        this.updateModalContent(data);
        
        // Show modal
        const bootstrapModal = new bootstrap.Modal(modal);
        bootstrapModal.show();
        
        // Create confetti animation
        this.createConfetti();
        
        // Play success sound if available
        this.playSuccessSound();
    }

    updateModalContent(data) {
        // Points and activity
        const pointsEl = document.getElementById('popupPoints');
        const newPoints = data.points_earned;
        pointsEl.textContent = `+${newPoints}`;
        
        // Activity description
        const activityTypes = {
            'listing_created': 'Added Material Listing',
            'photo_added': 'Added Photo',
            'deal_completed': 'Completed a Deal',
            'review_written': 'Wrote a Review',
            'story_created': 'Created Ski Story',
            'partner_post_created': 'Posted Ski Partner Ad',
            'condition_report': 'Submitted Condition Report',
            'friend_added': 'Added Friend',
            'instructor_service': 'Completed Instructor Service',
            'review_received': 'Received a Review',
            'daily_login': 'Daily Login',
            'profile_completed': 'Completed Profile',
            'first_login': 'First Login'
        };
        
        const activityName = activityTypes[data.activity_type] || data.activity_type.replace(/_/g, ' ');
        document.getElementById('popupActivityType').textContent = activityName;
        
        // Level and stats
        if (data.stats) {
            const stats = data.stats;
            document.getElementById('popupLevel').textContent = stats.level || 8;
            document.getElementById('popupTotalPoints').textContent = (stats.total_points || 0).toLocaleString();
            
            // XP progress
            const nextLevelPoints = (stats.level + 1) ** 2 * 100;
            const currentLevelPoints = stats.level ** 2 * 100;
            const xpInLevel = Math.max(0, stats.total_points - currentLevelPoints);
            const xpNeeded = nextLevelPoints - currentLevelPoints;
            const xpPercent = (xpInLevel / xpNeeded) * 100;
            
            document.getElementById('popupXpBar').style.width = Math.min(xpPercent, 100) + '%';
            document.getElementById('popupXpText').textContent = 
                `${xpInLevel} / ${xpNeeded} XP to Level ${stats.level + 1}`;
            
            // Activity stats
            document.getElementById('popupListings').textContent = stats.total_listings_created || 0;
            document.getElementById('popupDeals').textContent = stats.total_deals_completed || 0;
            document.getElementById('popupRating').textContent = '⭐ ' + (stats.average_seller_rating || 'N/A');
        }
        
        // Badge unlock
        if (data.badge) {
            const badgeContainer = document.getElementById('badgeUnlockContainer');
            document.getElementById('popupBadgeEmoji').textContent = data.badge.icon_emoji || '🏆';
            document.getElementById('popupBadgeName').querySelector('strong').textContent = data.badge.name;
            document.getElementById('popupBadgeDesc').textContent = data.badge.requirement_description || '';
            badgeContainer.style.display = 'block';
        } else {
            document.getElementById('badgeUnlockContainer').style.display = 'none';
        }
    }

    createConfetti() {
        const container = document.querySelector('.confetti-container');
        container.innerHTML = ''; // Clear previous confetti
        
        const colors = ['#2ecc71', '#3498db', '#f39c12', '#e74c3c', '#9b59b6'];
        
        for (let i = 0; i < 30; i++) {
            const confetti = document.createElement('div');
            confetti.className = 'confetti';
            confetti.style.left = Math.random() * 100 + '%';
            confetti.style.backgroundColor = colors[Math.floor(Math.random() * colors.length)];
            confetti.style.animationDelay = (Math.random() * 0.5) + 's';
            confetti.style.animationDuration = (2 + Math.random() * 0.5) + 's';
            container.appendChild(confetti);
        }
    }

    playSuccessSound() {
        // Create a simple success beep using Web Audio API
        try {
            const audioContext = new (window.AudioContext || window.webkitAudioContext)();
            const oscillator = audioContext.createOscillator();
            const gainNode = audioContext.createGain();
            
            oscillator.connect(gainNode);
            gainNode.connect(audioContext.destination);
            
            oscillator.frequency.value = 800;
            oscillator.type = 'sine';
            
            gainNode.gain.setValueAtTime(0.3, audioContext.currentTime);
            gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.1);
            
            oscillator.start(audioContext.currentTime);
            oscillator.stop(audioContext.currentTime + 0.1);
        } catch (e) {
            // Silently fail if Web Audio API not available
        }
    }
}

// Initialize on document ready
document.addEventListener('DOMContentLoaded', function() {
    window.pointsPopup = new PointsEarnedPopup();
});
