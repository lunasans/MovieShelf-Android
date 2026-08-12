package info.movieshelf.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import info.movieshelf.R
import info.movieshelf.MovieShelfApplication
import info.movieshelf.data.model.Stats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(
            (LocalContext.current.applicationContext as MovieShelfApplication).movieRepository
        )
    )
) {
    val stats = viewModel.stats
    val isLoading = viewModel.isLoading
    val error = viewModel.error

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null && stats == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CloudOff,
                        null,
                        Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(error.asString(), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadStats() }) { Text(stringResource(R.string.common_retry)) }
                }
            }
        } else if (stats != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Übersichtskarten
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.stats_total),
                        value = stats.totalFilms.toString(),
                        subtitle = stringResource(R.string.shelf_movies),
                        icon = Icons.Default.Movie,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.stats_watched),
                        value = stats.watched?.count.toString(),
                        subtitle = "${stats.watched?.percentage?.toInt()}%",
                        icon = Icons.Default.Visibility,
                        color = Color(0xFF4CAF50)
                    )
                }

                // Serien stehen fuer sich: die Film-Zahlen oben schliessen sie
                // nicht ein, sonst wichen sie von Shelf und Desktop-App ab.
                if (stats.totalSeries > 0) {
                    StatCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(R.string.stats_series),
                        value = stats.totalSeries.toString(),
                        subtitle = if (stats.totalSeries == 1) "Serie in der Sammlung" else "Serien in der Sammlung",
                        icon = Icons.Default.Tv,
                        color = Color(0xFF7C4DFF)
                    )
                }

                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.stats_total_runtime),
                    value = "${stats.totalRuntimeDays.toInt()} Tage",
                    subtitle = stringResource(R.string.stats_hours, stats.totalRuntimeHours.toInt()),
                    icon = Icons.Default.AccessTime,
                    color = Color(0xFFFF9800)
                )

                // Zeitreise
                Text(stringResource(R.string.stats_timeline), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatRow(label = stringResource(R.string.stats_oldest), value = stats.years?.oldestYear?.toString() ?: "-")
                        StatRow(label = stringResource(R.string.stats_newest), value = stats.years?.newestYear?.toString() ?: "-")
                        StatRow(label = stringResource(R.string.stats_avg_year), value = stats.years?.avgYear?.toInt()?.toString() ?: "-")
                    }
                }

                // Top Genres
                if (!stats.genres.isNullOrEmpty()) {
                    Text(stringResource(R.string.stats_top_genres), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stats.genres.take(5).forEach { genre ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(genre.genre)
                                    Text(genre.count.toString(), fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { genre.count.toFloat() / stats.totalFilms.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        }
                    }
                }

                // Jahrzehnte
                if (!stats.decades.isNullOrEmpty()) {
                    Text(stringResource(R.string.stats_decades), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stats.decades.sortedByDescending { it.decade }.forEach { decade ->
                                StatRow(label = stringResource(R.string.stats_decade, decade.decade), value = "${decade.count} Filme")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(title, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.7f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
