package com.grenobleski.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class PointsEarnedData(
    val pointsEarned: Int,
    val activityType: String,
    val stats: UserStatsData,
    val badge: BadgeUnlockedData? = null
)

data class UserStatsData(
    val level: Int,
    val totalPoints: Int,
    val experiencePoints: Int,
    val badgesCount: Int,
    val totalListingsCreated: Int,
    val totalDealsCompleted: Int,
    val totalReviewsWritten: Int,
    val averageSellerRating: String
)

data class BadgeUnlockedData(
    val iconEmoji: String,
    val name: String,
    val requirementDescription: String,
    val rarity: String,
    val pointsValue: Int
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PointsEarnedDialog(
    data: PointsEarnedData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(true) }
    var showConfetti by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        // Hide confetti after animation completes
        kotlinx.coroutines.delay(2000)
        showConfetti = false
    }
    
    if (showDialog) {
        Dialog(
            onDismissRequest = {
                showDialog = false
                onDismiss()
            },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 650.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Scrollable content area
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        // Header with celebration (non-scrollable)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF27ae60), Color(0xFF2ecc71))
                                    )
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Confetti animation
                            if (showConfetti) {
                                repeat(30) {
                                    val angle = (360f / 30) * it
                                    val duration = Random.nextInt(1500, 2500)
                                    ConfettiPiece(angle = angle, duration = duration)
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("")  // Spacer
                                
                                Text(
                                    text = "🎉 Points Earned!",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                
                                IconButton(onClick = { showDialog = false }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        
                        // Body content (scrollable only in this section)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        // Big points display with animation
                        AnimatedContent(
                            targetState = data.pointsEarned,
                            transitionSpec = {
                                slideInVertically { -it } + fadeIn() togetherWith 
                                slideOutVertically { it } + fadeOut()
                            }
                        ) { points ->
                            Text(
                                text = "+$points",
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF27ae60),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        
                        // Activity type
                        Text(
                            text = getActivityTypeName(data.activityType),
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        
                        // Level and XP card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // Level
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "YOUR LEVEL",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = data.stats.level.toString(),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF27ae60)
                                        )
                                    }
                                    
                                    // Total points
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "TOTAL POINTS",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${data.stats.totalPoints}",
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF27ae60)
                                        )
                                    }
                                }
                                
                                // XP Progress bar
                                Spacer(modifier = Modifier.height(12.dp))
                                val nextLevelPoints = (data.stats.level + 1) * (data.stats.level + 1) * 100
                                val currentLevelPoints = data.stats.level * data.stats.level * 100
                                val xpInLevel = maxOf(0, data.stats.totalPoints - currentLevelPoints)
                                val xpNeeded = nextLevelPoints - currentLevelPoints
                                val xpPercent = (xpInLevel.toFloat() / xpNeeded).coerceIn(0f, 1f)
                                
                                LinearProgressIndicator(
                                    progress = { xpPercent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF2ecc71),
                                    trackColor = Color.LightGray
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "$xpInLevel / $xpNeeded XP to Level ${data.stats.level + 1}",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        // Badge unlock section (if applicable)
                        data.badge?.let { badge ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFEBF5FB)
                                ),
                                border = BorderStroke(2.dp, Color(0xFF3498db)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🏆 Badge Unlocked!",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2c3e50),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    
                                    Text(
                                        text = badge.iconEmoji,
                                        fontSize = 48.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    Text(
                                        text = badge.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2c3e50),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    
                                    Text(
                                        text = badge.requirementDescription,
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        
                        // Stats summary
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                label = "Listings",
                                value = data.stats.totalListingsCreated.toString()
                            )
                            StatItem(
                                label = "Deals",
                                value = data.stats.totalDealsCompleted.toString()
                            )
                            StatItem(
                                label = "Rating",
                                value = "⭐ ${data.stats.averageSellerRating}"
                            )
                        }
                        }
                    }
                    
                    // Footer button (non-scrollable, fixed at bottom)
                    Button(
                        onClick = { showDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF27ae60)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Awesome! 🚀",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
            .padding(12.dp)
            .width(80.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF27ae60),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ConfettiPiece(angle: Float, duration: Int) {
    val randomX = remember { Random.nextFloat() * 2f - 1f }
    val randomSize = remember { Random.nextInt(4, 10) }
    val randomColor = remember {
        listOf(
            Color(0xFF2ecc71),
            Color(0xFF3498db),
            Color(0xFFF39c12),
            Color(0xFFe74c3c),
            Color(0xFF9b59b6)
        ).random()
    }
    
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = duration,
            easing = androidx.compose.animation.core.LinearEasing
        )
    )
    
    val xOffset = randomX * 100 * animationProgress * cos(Math.toRadians(angle.toDouble())).toFloat()
    val yOffset = 100 * animationProgress * sin(Math.toRadians(angle.toDouble())).toFloat()
    
    Box(
        modifier = Modifier
            .size(randomSize.dp)
            .background(randomColor, RoundedCornerShape(2.dp))
            .offset(x = xOffset.dp, y = yOffset.dp)
            .alpha((1f - animationProgress).coerceAtLeast(0f))
    )
}

fun getActivityTypeName(activityType: String): String {
    return when (activityType) {
        "listing_created" -> "Added Material Listing"
        "photo_added" -> "Added Photo"
        "deal_completed" -> "Completed a Deal"
        "review_written" -> "Wrote a Review"
        "review_received" -> "Received a Review"
        "story_created" -> "Created Ski Story"
        "partner_post_created" -> "Posted Ski Partner Ad"
        "condition_report" -> "Submitted Condition Report"
        "friend_added" -> "Added Friend"
        "instructor_service" -> "Completed Instructor Service"
        "daily_login" -> "Daily Login"
        "profile_completed" -> "Completed Profile"
        "first_login" -> "First Login"
        else -> activityType.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}
