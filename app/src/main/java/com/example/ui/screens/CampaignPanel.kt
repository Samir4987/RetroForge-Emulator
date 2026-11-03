package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emulator.CampaignView

@Composable
fun CampaignPanel(view: CampaignView) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = Color(0xFF171D2B),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = if (view.completed) "CAMPAIGN COMPLETE" else "CHAPTER ${view.level}/3 • ${view.levelTitle}",
                color = if (view.completed) Color(0xFF69F0AE) else Color(0xFF80D8FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = view.story,
                color = Color(0xFFE0E5F2),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                text = when {
                    view.completed -> view.message
                    view.bossActive -> "${view.bossName} • HP ${view.bossHealth}/${view.bossHealthMax} — ${view.bossStory}"
                    else -> "${view.objective}: ${view.objectiveProgress}/${view.objectiveGoal}"
                },
                color = if (view.bossActive) Color(0xFFFFCC80) else Color(0xFFB7C2D8),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = if (view.bossActive || view.completed) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
