package info.movieshelf.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import info.movieshelf.R
import info.movieshelf.MovieShelfApplication
import info.movieshelf.data.model.Stats
import info.movieshelf.ui.theme.PillShape
import kotlin.math.roundToInt

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
        } else if (stats != null && stats.totalFilms == 0 && stats.totalSeries == 0) {
            // Eine Statistik ueber nichts besteht sonst aus lauter Nullen und
            // leeren Balken — das sieht nach Fehler aus, ist aber keiner.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(Icons.Default.QueryStats, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.stats_empty_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.stats_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (stats != null) {
            StatsContent(stats = stats, contentPadding = padding)
        }
    }
}

@Composable
private fun StatsContent(stats: Stats, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- Kennzahlen ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.stats_total),
                value = stats.totalFilms.toString(),
                subtitle = stringResource(R.string.shelf_movies),
                icon = Icons.Default.Movie
            )
            // Serien stehen fuer sich: die Film-Zahlen schliessen sie nicht ein,
            // sonst wichen sie von Shelf und Desktop-App ab.
            if (stats.totalSeries > 0) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.stats_series),
                    value = stats.totalSeries.toString(),
                    subtitle = stringResource(
                        if (stats.totalSeries == 1) R.string.stats_series_one else R.string.stats_series_many
                    ),
                    icon = Icons.Default.Tv
                )
            } else {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.stats_avg_runtime),
                    value = stringResource(R.string.stats_minutes, stats.avgRuntime.roundToInt()),
                    subtitle = stringResource(R.string.stats_per_film),
                    icon = Icons.Default.Timer
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.stats_total_runtime),
                value = stringResource(R.string.stats_days, stats.totalRuntimeDays.roundToInt()),
                subtitle = stringResource(R.string.stats_hours, stats.totalRuntimeHours.toInt()),
                icon = Icons.Default.AccessTime
            )
            if (stats.totalSeries > 0) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.stats_avg_runtime),
                    value = stringResource(R.string.stats_minutes, stats.avgRuntime.roundToInt()),
                    subtitle = stringResource(R.string.stats_per_film),
                    icon = Icons.Default.Timer
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        // --- Gesehen ---
        stats.watched?.let { watched ->
            StatsSection(title = stringResource(R.string.stats_watched)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = watched.count.toString(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${watched.percentage.roundToInt()} %",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (watched.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.stats_unwatched, (stats.totalFilms - watched.count).coerceAtLeast(0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Zusammensetzung der Sammlung ---
        stats.collections?.takeIf { it.isNotEmpty() }?.let { collections ->
            StatsSection(title = stringResource(R.string.stats_collection_types)) {
                val max = collections.maxOf { it.count }
                collections.sortedByDescending { it.count }.forEach { entry ->
                    BarRow(
                        label = entry.collectionType,
                        value = entry.count.toString(),
                        fraction = entry.count.toFloat() / max.toFloat(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- Genres ---
        stats.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            StatsSection(title = stringResource(R.string.stats_top_genres)) {
                // Die Balken messen sich am haeufigsten Genre, nicht an der
                // Gesamtzahl: sonst bleiben bei einer breit gestreuten Sammlung
                // alle Balken Striche und die Reihenfolge ist nicht ablesbar.
                val max = genres.maxOf { it.count }
                genres.sortedByDescending { it.count }.take(8).forEach { genre ->
                    BarRow(
                        label = genre.genre,
                        value = genre.count.toString(),
                        fraction = genre.count.toFloat() / max.toFloat(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- Jahrzehnte ---
        stats.decades?.takeIf { it.isNotEmpty() }?.let { decades ->
            StatsSection(title = stringResource(R.string.stats_decades)) {
                ColumnChart(
                    entries = decades.sortedBy { it.decade }.map {
                        ChartEntry(label = stringResource(R.string.stats_decade, it.decade), count = it.count)
                    },
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // --- Jahre ---
        stats.yearDistribution?.takeIf { it.isNotEmpty() }?.let { years ->
            StatsSection(title = stringResource(R.string.stats_per_year)) {
                ColumnChart(
                    entries = years.entries
                        .mapNotNull { (year, count) -> year.toIntOrNull()?.let { it to count } }
                        .sortedBy { it.first }
                        .map { ChartEntry(label = it.first.toString(), count = it.second) },
                    color = MaterialTheme.colorScheme.primary,
                    barWidth = 18.dp
                )
            }
        }

        // --- Altersfreigabe ---
        stats.ratings?.takeIf { it.isNotEmpty() }?.let { ratings ->
            StatsSection(title = stringResource(R.string.stats_age_rating)) {
                val max = ratings.maxOf { it.count }
                ratings.sortedBy { it.ratingAge }.forEach { rating ->
                    BarRow(
                        label = stringResource(R.string.stats_fsk, rating.ratingAge),
                        value = rating.count.toString(),
                        fraction = rating.count.toFloat() / max.toFloat(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- Zeitreise ---
        stats.years?.let { years ->
            StatsSection(title = stringResource(R.string.stats_timeline)) {
                StatRow(label = stringResource(R.string.stats_oldest), value = years.oldestYear.toString())
                Spacer(Modifier.height(6.dp))
                StatRow(label = stringResource(R.string.stats_newest), value = years.newestYear.toString())
                Spacer(Modifier.height(6.dp))
                StatRow(label = stringResource(R.string.stats_avg_year), value = years.avgYear.roundToInt().toString())
            }
        }
    }
}

/** Abschnitt mit Überschrift und Karte — die Seite besteht nur aus diesen. */
@Composable
private fun StatsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(modifier = Modifier.fillMaxWidth(), shape = PillShape) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

/**
 * Waagerechter Balken mit Beschriftung — fuer Auswertungen mit wenigen, aber
 * langen Beschriftungen (Genres, Sammlungsarten, Altersfreigaben).
 */
@Composable
private fun BarRow(label: String, value: String, fraction: Float, color: Color) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(12.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
    }
}

private data class ChartEntry(val label: String, val count: Int)

/**
 * Stehende Balken fuer Reihen ueber die Zeit (Jahrzehnte, Jahre).
 *
 * Scrollt waagerecht, statt die Balken zu stauchen: bei vierzig Jahrgaengen
 * waere sonst jeder Balken zwei Pixel breit und keine Beschriftung mehr lesbar.
 */
@Composable
private fun ColumnChart(
    entries: List<ChartEntry>,
    color: Color,
    barWidth: androidx.compose.ui.unit.Dp = 30.dp
) {
    if (entries.isEmpty()) return
    val max = entries.maxOf { it.count }.coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        entries.forEach { entry ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(barWidth)
            ) {
                Text(
                    text = entry.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(barWidth * 0.7f)
                        // Mindesthoehe, damit ein einzelner Film neben einem
                        // Jahrgang mit dreissig nicht ganz verschwindet.
                        .height((96.dp * entry.count / max).coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Kennzahl-Karte.
 *
 * Bewusst ohne eigene Farbe: die Oberflaeche kennt genau einen Akzent
 * (das Marken-Rose), und der gehoert hier dem Icon. Eine Karte je Kennzahl
 * einzufaerben machte aus der Seite ein Farbmuster, in dem die Farbe nichts
 * mehr bedeutet — und ueberginge zugleich Material You, wo der Akzent vom
 * Nutzer kommt.
 */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        shape = PillShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
