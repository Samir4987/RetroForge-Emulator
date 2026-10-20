package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ScannedRomEntity

@Composable
fun BoxArtCard(
    rom: ScannedRomEntity,
    onPlay: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = Color(rom.boxArtPrimaryColor)
    val secondaryColor = Color(rom.boxArtSecondaryColor)

    Card(
        modifier = modifier
            .testTag("box_art_card_${rom.id}")
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF33374D), RoundedCornerShape(12.dp))
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141624))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // System Banner Header (e.g. Game Boy, Game Boy Advance, Nintendo 64)
            SystemBrandingBanner(system = rom.system)

            // Main Box Art Illustration Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.85f), secondaryColor, Color(0xFF0C0D17))
                        )
                    )
                    .padding(8.dp)
            ) {
                // Subtle holographic background grid lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 20.dp.toPx()
                    for (x in 0..(size.width / step).toInt()) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(x * step, 0f),
                            end = Offset(x * step, size.height),
                            strokeWidth = 1f
                        )
                    }
                }

                // Center Game Mascot Emoji / Art Emblem
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rom.iconEmoji,
                        fontSize = 32.sp
                    )
                }

                // Official Seal of Quality (Gold Medal)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFE5A93B).copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "★ OFFICIAL SEAL",
                        color = Color.Black,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // ESRB / Rating badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = "EVERYONE",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Box Bottom: Title, Publisher, Info & Play Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = rom.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${rom.publisher} • ${rom.releaseYear}",
                        color = Color(0xFF9094A8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row {
                        IconButton(
                            onClick = onShowDetails,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("info_button_${rom.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Game Info",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onPlay,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("play_button_${rom.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemBrandingBanner(system: String) {
    val (bgColor, textColor, label) = when (system.uppercase()) {
        "GB" -> Triple(Color(0xFF9E9E9E), Color(0xFF1E1E1E), "GAME BOY™")
        "GBC" -> Triple(Color(0xFF6C3483), Color(0xFFFFD54F), "GAME BOY COLOR™")
        "GBA" -> Triple(Color(0xFF154360), Color(0xFF85C1E9), "GAME BOY ADVANCE™")
        "NDS" -> Triple(Color(0xFF212529), Color(0xFF00E5FF), "NINTENDO DS™")
        "N64" -> Triple(Color(0xFF881B0E), Color(0xFFFFCC00), "NINTENDO 64™")
        else -> Triple(Color(0xFF1C1E2D), Color.White, system)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                fontStyle = FontStyle.Italic,
                letterSpacing = 1.sp
            )
            Text(
                text = "RETRO",
                color = textColor.copy(alpha = 0.8f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
