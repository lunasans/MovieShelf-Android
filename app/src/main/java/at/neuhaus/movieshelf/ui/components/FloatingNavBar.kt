package at.neuhaus.movieshelf.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.neuhaus.movieshelf.ui.theme.NavAccentRed
import at.neuhaus.movieshelf.ui.theme.NavAccentRedDark
import at.neuhaus.movieshelf.ui.theme.OnSurfaceVariantDark

@Composable
fun FloatingNavBar(
    modifier: Modifier = Modifier,
    currentRoute: String?,
    onHomeClick: () -> Unit,
    onStatsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onAddClick: () -> Unit,
    /** Ohne Konto gibt es nichts abzumelden. */
    showLogout: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Haupt-Pill: frosted glass statt Verlaufs-Fläche ("Shelf"-Look).
        // Bewusst ohne .shadow() – die Elevation-Schlagschatten-API rendert auf
        // manchen Geräten/Emulatoren als grauer Blob statt als sanftes Glow;
        // die Glasfläche wirkt durch Transparenz + hellen Rand bereits erhaben.
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavPillItem(
                    icon = Icons.Default.Home,
                    label = "Filme",
                    selected = currentRoute == "dashboard",
                    onClick = onHomeClick
                )
                NavPillItem(
                    icon = Icons.Default.BarChart,
                    label = "Statistik",
                    selected = currentRoute == "stats",
                    onClick = onStatsClick
                )

                // Zentraler Add-Button
                AddCenterButton(onClick = onAddClick)

                NavPillItem(
                    icon = Icons.Default.Person,
                    label = "Profil",
                    selected = currentRoute == "profile",
                    onClick = onProfileClick
                )
                if (showLogout) NavPillItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Abmelden",
                    selected = false,
                    // Abmelden hebt sich neutral-grau ab: Rosé ist jetzt der
                    // Marken-Akzent der ganzen Leiste und wäre hier doppelt.
                    accentColor = OnSurfaceVariantDark,
                    onClick = onLogoutClick
                )
            }
        }
    }
}

@Composable
private fun NavPillItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color = NavAccentRed
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
        animationSpec = tween(200),
        label = "nav_icon_color"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.9f else 0f,
        animationSpec = tween(200),
        label = "nav_bg_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "nav_scale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = bgAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (accentColor == NavAccentRed) iconColor else accentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (accentColor == NavAccentRed) iconColor else accentColor.copy(alpha = 0.85f),
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun AddCenterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = NavAccentRed.copy(alpha = 0.4f),
                spotColor = NavAccentRed.copy(alpha = 0.6f)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(NavAccentRed, NavAccentRedDark)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Film hinzufügen",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}
