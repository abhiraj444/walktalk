package com.walktalk.ui.talk

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.walktalk.WalkTalkApp

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun PttCallScreen(
    contactName: String,
    locationContext: String = "Nearby",
    isHandsFreeLocked: Boolean,
    onPttPressStart: () -> Unit,
    onPttPressEnd: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onQuickReactionSent: (String) -> Unit,
    onEndCall: () -> Unit
) {
    var isPressingPtt by remember { mutableStateOf(false) }

    // Waveform breathing animation when transmitting
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPressingPtt || isHandsFreeLocked) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Section: Contact Info
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = contactName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "📍 $locationContext",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Waveform indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isPressingPtt -> "🎙️ TRANSMITTING AUDIO..."
                        isHandsFreeLocked -> "🔓 HANDS-FREE ACTIVE"
                        else -> "≋ LISTENING FOR AUDIO ≋"
                    },
                    color = if (isPressingPtt || isHandsFreeLocked) Color(0xFF00E676) else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Quick Reactions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickReactionButton(emoji = "✅", label = "OK") { onQuickReactionSent("OK") }
            QuickReactionButton(emoji = "🏃", label = "Coming!") { onQuickReactionSent("Coming!") }
            QuickReactionButton(emoji = "⏰", label = "5 min") { onQuickReactionSent("5 min") }
            QuickReactionButton(emoji = "❌", label = "Busy") { onQuickReactionSent("Busy") }
        }

        // Bottom Section: Giant PTT Button & Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Hands-Free Lock Toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isHandsFreeLocked) Color(0xFF00E676) else Color(0xFF2C3E50))
                    .clickable { onToggleHandsFree() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isHandsFreeLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isHandsFreeLocked) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHandsFreeLocked) "LOCKED (HANDS-FREE)" else "TAP TO LOCK MIC",
                    color = if (isHandsFreeLocked) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Giant Push-to-Talk Button
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        if (isPressingPtt || isHandsFreeLocked) Color(0xFF00E676) else Color(0xFF2C3E50)
                    )
                    .pointerInteropFilter { motionEvent ->
                        when (motionEvent.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isPressingPtt = true
                                WalkTalkApp.instance.audioFeedback.playPttStartChirp()
                                onPttPressStart()
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isPressingPtt = false
                                WalkTalkApp.instance.audioFeedback.playPttReleaseClick()
                                onPttPressEnd()
                                true
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "PTT Mic",
                        tint = if (isPressingPtt || isHandsFreeLocked) Color.Black else Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPressingPtt) "TALKING" else "HOLD TO TALK",
                        color = if (isPressingPtt || isHandsFreeLocked) Color.Black else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // End Call Button
            IconButton(
                onClick = onEndCall,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF1744))
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun QuickReactionButton(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Text(text = label, color = Color.Gray, fontSize = 11.sp)
    }
}
