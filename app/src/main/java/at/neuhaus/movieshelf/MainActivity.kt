package at.neuhaus.movieshelf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import at.neuhaus.movieshelf.ui.components.FloatingNavBar
import at.neuhaus.movieshelf.data.SessionManager
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.local.DataStoreManager
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.ui.about.AboutScreen
import at.neuhaus.movieshelf.ui.actors.ActorDetailScreen
import at.neuhaus.movieshelf.ui.add.AddMovieScreen
import at.neuhaus.movieshelf.ui.dashboard.DashboardScreen
import at.neuhaus.movieshelf.ui.details.MovieDetailScreen
import at.neuhaus.movieshelf.ui.create.CreateMovieScreen
import at.neuhaus.movieshelf.ui.edit.EditMovieScreen
import at.neuhaus.movieshelf.ui.lists.ListDetailScreen
import at.neuhaus.movieshelf.ui.lists.ListsScreen
import at.neuhaus.movieshelf.ui.login.LoginScreen
import at.neuhaus.movieshelf.ui.twofactor.TwoFactorScreen
import at.neuhaus.movieshelf.ui.profile.ProfileScreen
import at.neuhaus.movieshelf.ui.setup.SetupScreen
import at.neuhaus.movieshelf.ui.stats.StatsScreen
import at.neuhaus.movieshelf.ui.theme.MovieShelfTheme
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    val oauthCallbackUri = mutableStateOf<Uri?>(null)

    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
    private val installListener = InstallStateUpdatedListener { state ->
        // Flexibles Update fertig heruntergeladen -> installieren.
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            runCatching { appUpdateManager.completeUpdate() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        checkForAppUpdate()

        setContent {
            val dsm = remember { DataStoreManager(applicationContext) }
            val dynamicColor by dsm.dynamicColor.collectAsState(initial = false)
            MovieShelfTheme(dynamicColor = dynamicColor) {
                MovieShelfApp(oauthCallbackUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Prüft via Google Play, ob ein App-Update verfügbar ist, und startet einen
     * flexiblen Update-Flow (Download im Hintergrund). No-op, wenn nicht über Play
     * installiert oder kein Update vorhanden.
     */
    private fun checkForAppUpdate() {
        runCatching {
            appUpdateManager.registerListener(installListener)
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        appUpdateManager.startUpdateFlowForResult(info, AppUpdateType.FLEXIBLE, this, 4711)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { appUpdateManager.unregisterListener(installListener) }
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        // Nur den erwarteten OAuth-Callback akzeptieren (Scheme + Host + Pfad prüfen),
        // um manipulierte Deeplinks abzuweisen.
        if (uri.scheme == "movieshelf" && uri.host == "oauth" && uri.path == "/callback") {
            oauthCallbackUri.value = uri
        }
    }
}

private val slideEnter = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = tween(300)
) + fadeIn(animationSpec = tween(300))

private val slideExit = slideOutHorizontally(
    targetOffsetX = { -it / 3 },
    animationSpec = tween(300)
) + fadeOut(animationSpec = tween(200))

private val slidePopEnter = slideInHorizontally(
    initialOffsetX = { -it / 3 },
    animationSpec = tween(300)
) + fadeIn(animationSpec = tween(300))

private val slidePopExit = slideOutHorizontally(
    targetOffsetX = { it },
    animationSpec = tween(300)
) + fadeOut(animationSpec = tween(200))

private val fadeEnter = fadeIn(animationSpec = tween(250))
private val fadeExit = fadeOut(animationSpec = tween(200))

@Composable
fun MovieShelfApp(oauthCallbackUri: MutableState<Uri?> = mutableStateOf(null)) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val serverUrl by dataStoreManager.serverUrl.collectAsState(initial = null)
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showNavBar = currentRoute in listOf("dashboard", "profile", "stats")

    // Steuert das Ein-/Ausblenden der unteren NavBar beim Scrollen.
    var bottomBarVisible by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < -1f) bottomBarVisible = false
                else if (available.y > 1f) bottomBarVisible = true
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // Beim Screen-Wechsel die NavBar wieder einblenden.
    LaunchedEffect(currentRoute) { bottomBarVisible = true }

    var isInitialized by remember { mutableStateOf(false) }
    var initializationError by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf("login") }
    var isLoadingAuth by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Wenn der Server ein Token mit 401 ablehnt (abgelaufen/widerrufen): Token löschen
    // und zum Login zurückkehren, statt in einem Screen mit lauter 401-Fehlern zu landen.
    val sessionExpired by SessionManager.sessionExpired.collectAsState()
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) {
            dataStoreManager.saveAuthToken(null)
            SessionManager.resetExpiredFlag()
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // OAuth-Callback wird grundsätzlich auf dem Login-Screen verarbeitet. Falls er
    // eintrifft, während gerade ein anderer Screen aktiv ist, dorthin navigieren.
    LaunchedEffect(oauthCallbackUri.value) {
        if (oauthCallbackUri.value != null && isInitialized && currentRoute != null && currentRoute != "login") {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(serverUrl) {
        if (serverUrl.isNullOrBlank()) {
            isInitialized = false
            isLoadingAuth = false
            return@LaunchedEffect
        }

        val success = RetrofitClient.initialize(serverUrl!!, context)
        if (success) {
            val savedToken = dataStoreManager.authToken.first()
            if (!savedToken.isNullOrBlank()) {
                SessionManager.token = savedToken
                startDestination = "dashboard"
                // Profil (inkl. is_admin) im Hintergrund nachladen; bei 401 greift der Auto-Logout
                launch {
                    try { SessionManager.user = RetrofitClient.api.getUser() } catch (_: Exception) {}
                }
            } else {
                startDestination = "login"
            }
            isInitialized = true
            initializationError = false
        } else {
            isInitialized = false
            initializationError = true
        }
        isLoadingAuth = false
    }

    if (serverUrl.isNullOrBlank() || initializationError) {
        SetupScreen(
            dataStoreManager = dataStoreManager,
            onSetupComplete = { initializationError = false }
        )
    } else if (!isInitialized || isLoadingAuth) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit }
            ) {
                composable(
                    "login",
                    enterTransition = { fadeEnter },
                    exitTransition = { fadeExit },
                    popEnterTransition = { fadeEnter },
                    popExitTransition = { fadeExit }
                ) {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate("dashboard") {
                                popUpTo("login") { inclusive = true }
                            }
                        },
                        onResetUrl = {
                            scope.launch {
                                dataStoreManager.saveServerUrl("")
                                dataStoreManager.saveAuthToken(null)
                            }
                        },
                        oauthCallbackUri = oauthCallbackUri.value,
                        onOAuthCallbackConsumed = { oauthCallbackUri.value = null }
                    )
                }
                composable(
                    "dashboard",
                    enterTransition = { fadeEnter },
                    exitTransition = { fadeExit },
                    popEnterTransition = { fadeEnter },
                    popExitTransition = { fadeExit }
                ) { backStackEntry ->
                    // Wird hochgezählt, wenn ein Film gelöscht wurde -> Liste neu laden
                    val refreshKey by backStackEntry.savedStateHandle
                        .getStateFlow("needs_refresh", 0)
                        .collectAsState()

                    DashboardScreen(
                        reloadKey = refreshKey,
                        onMovieClick = { movie: Movie, allIds: List<Int> ->
                            val idsString = allIds.joinToString(",")
                            navController.navigate("movie_details/${movie.id}?allIds=$idsString")
                        },
                        onAboutClick = { navController.navigate("about") }
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        onBack = { navController.popBackStack() },
                        onListsClick = { navController.navigate("lists") },
                        onTwoFactorClick = { navController.navigate("twofactor") }
                    )
                }
                composable("twofactor") {
                    TwoFactorScreen(onBack = { navController.popBackStack() })
                }
                composable("create_movie") {
                    CreateMovieScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { newId ->
                            navController.navigate("movie_details/$newId") {
                                popUpTo("create_movie") { inclusive = true }
                            }
                        }
                    )
                }
                composable("stats") {
                    StatsScreen(onBack = { navController.popBackStack() })
                }
                composable("lists") {
                    ListsScreen(
                        onBack = { navController.popBackStack() },
                        onListClick = { listId -> navController.navigate("list_detail/$listId") }
                    )
                }
                composable(
                    "list_detail/{listId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("listId") { type = androidx.navigation.NavType.IntType }
                    )
                ) { backStackEntry ->
                    val listId = backStackEntry.arguments?.getInt("listId") ?: 0
                    ListDetailScreen(
                        listId = listId,
                        onBack = { navController.popBackStack() },
                        onMovieClick = { movie: Movie -> navController.navigate("movie_details/${movie.id}") }
                    )
                }
                composable(
                    "movie_details/{movieId}?allIds={allIds}",
                    arguments = listOf(
                        androidx.navigation.navArgument("movieId") { type = androidx.navigation.NavType.IntType },
                        androidx.navigation.navArgument("allIds") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
                    val allIdsString = backStackEntry.arguments?.getString("allIds")
                    val allMovieIds = allIdsString?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                    // Signal vom Edit-Screen: hochgezählt, sobald ein Film bearbeitet wurde
                    val reloadKey by backStackEntry.savedStateHandle
                        .getStateFlow("movie_edited", 0)
                        .collectAsState()

                    MovieDetailScreen(
                        movieId = movieId,
                        allMovieIds = allMovieIds,
                        reloadKey = reloadKey,
                        onBack = { navController.popBackStack() },
                        onEditClick = { id -> navController.navigate("edit_movie/$id") },
                        onMovieClick = { movie: Movie ->
                            navController.navigate("movie_details/${movie.id}")
                        },
                        onActorClick = { actorId ->
                            navController.navigate("actor_details/$actorId")
                        },
                        onActorNameClick = { actorName ->
                            scope.launch {
                                try {
                                    val response = RetrofitClient.api.searchActors(actorName)
                                    val foundActor = response.data?.firstOrNull()
                                    if (foundActor?.id != null) {
                                        navController.navigate("actor_details/${foundActor.id}")
                                    } else {
                                        Toast.makeText(context, "Schauspieler \"$actorName\" nicht gefunden", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Fehler bei der Suche", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
                composable("actor_details/{actorId}") { backStackEntry ->
                    val actorId = backStackEntry.arguments?.getString("actorId")?.toIntOrNull()
                    if (actorId != null) {
                        ActorDetailScreen(
                            actorId = actorId,
                            onBack = { navController.popBackStack() },
                            onMovieClick = { movie: Movie ->
                                navController.navigate("movie_details/${movie.id}")
                            }
                        )
                    }
                }
                composable("add_movie") {
                    AddMovieScreen(
                        onBack = { navController.popBackStack() },
                        onMovieImported = { navController.popBackStack() },
                        onCreateManual = { navController.navigate("create_movie") }
                    )
                }
                composable(
                    "edit_movie/{movieId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("movieId") { type = androidx.navigation.NavType.IntType }
                    )
                ) { backStackEntry ->
                    val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
                    EditMovieScreen(
                        movieId = movieId,
                        onBack = { navController.popBackStack() },
                        onSaved = {
                            // Detail-Screen über die Bearbeitung informieren, damit er neu lädt
                            navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                                handle["movie_edited"] = (handle.get<Int>("movie_edited") ?: 0) + 1
                            }
                            navController.popBackStack()
                        },
                        onDeleted = {
                            // Gelöscht: zurück zum Dashboard (Detail überspringen) und neu laden
                            runCatching {
                                navController.getBackStackEntry("dashboard").savedStateHandle.let { handle ->
                                    handle["needs_refresh"] = (handle.get<Int>("needs_refresh") ?: 0) + 1
                                }
                            }
                            navController.popBackStack("dashboard", inclusive = false)
                        }
                    )
                }
                composable("about") {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }

            AnimatedVisibility(
                visible = showNavBar && bottomBarVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                FloatingNavBar(
                    currentRoute = currentRoute,
                    onHomeClick = {
                        if (currentRoute != "dashboard") {
                            navController.navigate("dashboard") {
                                popUpTo("dashboard") { inclusive = false }
                            }
                        }
                    },
                    onStatsClick = {
                        if (currentRoute != "stats") {
                            navController.navigate("stats")
                        }
                    },
                    onProfileClick = {
                        if (currentRoute != "profile") {
                            navController.navigate("profile")
                        }
                    },
                    onLogoutClick = {
                        showLogoutDialog = true
                    },
                    onAddClick = {
                        navController.navigate("add_movie")
                    }
                )
            }

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null
                        )
                    },
                    title = { Text("Abmelden?") },
                    text = { Text("Möchtest du dich wirklich abmelden?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showLogoutDialog = false
                                scope.launch {
                                    dataStoreManager.saveAuthToken(null)
                                    SessionManager.token = null
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        ) {
                            Text("Abmelden")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }
        }
    }
}
