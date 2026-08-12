package info.movieshelf.ui.about

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import info.movieshelf.BuildConfig
import info.movieshelf.R
import info.movieshelf.data.api.RetrofitClient
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    // Vorab aufloesen: weder remember-Lambda noch LaunchedEffect sind ein
    // Composable-Kontext, in dem stringResource aufgerufen werden koennte.
    val loadingLabel = stringResource(R.string.about_loading)
    val unknownLabel = stringResource(R.string.common_unknown)
    val unreachableLabel = stringResource(R.string.about_unreachable)
    var serverVersion by remember { mutableStateOf(loadingLabel) }
    val uriHandler = LocalUriHandler.current
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    // Versionsname sicher aus BuildConfig lesen
    val appVersion = remember { 
        try {
            BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            "1.5.0"
        }
    }

    LaunchedEffect(Unit) {
        try {
            val info = RetrofitClient.api.getServerInfo()
            serverVersion = info.version ?: unknownLabel
        } catch (_: Exception) {
            serverVersion = unreachableLabel
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = stringResource(R.string.login_logo_description),
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Version Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.about_app_version), fontWeight = FontWeight.Bold)
                        Text(appVersion)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.about_server_version), fontWeight = FontWeight.Bold)
                        Text(serverVersion)
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))

            // GitHub Link
            OutlinedButton(
                onClick = { 
                    try {
                        uriHandler.openUri("https://github.com/lunasans/MovieShelf") 
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = stringResource(R.string.about_github),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.about_github_sub))
            }

            Spacer(Modifier.height(32.dp))
            
            Text(
                text = stringResource(R.string.about_tagline),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(Modifier.height(48.dp))
            
            // TMDb Attribution
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.about_powered_by),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(16.dp))
                    Image(
                        painter = painterResource(id = R.drawable.tmdb_logo),
                        contentDescription = stringResource(R.string.about_tmdb_logo),
                        modifier = Modifier
                            .width(100.dp)
                            .height(43.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_tmdb_note),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            Spacer(Modifier.height(56.dp))
            
            Text(
                text = stringResource(R.string.copyright, currentYear),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            
            Spacer(Modifier.height(16.dp))
        }
    }
}
