package info.movieshelf

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import info.movieshelf.ui.components.FloatingNavBar
import info.movieshelf.data.SessionManager
import info.movieshelf.data.api.RetrofitClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.stringResource
import info.movieshelf.R
import info.movieshelf.data.local.DataStoreManager
import info.movieshelf.data.local.ThemeMode
import info.movieshelf.data.model.Movie
import info.movieshelf.ui.about.AboutScreen
import info.movieshelf.data.model.Actor
import info.movieshelf.ui.actors.ActorDetailScreen
import info.movieshelf.ui.add.AddMovieScreen
import info.movieshelf.ui.dashboard.DashboardScreen
import info.movieshelf.ui.details.MovieDetailScreen
import info.movieshelf.ui.create.CreateMovieScreen
import info.movieshelf.ui.edit.EditMovieScreen
import info.movieshelf.ui.lists.ListDetailScreen
import info.movieshelf.ui.lists.ListsScreen
import info.movieshelf.ui.login.LoginScreen
import info.movieshelf.ui.twofactor.TwoFactorScreen
import info.movieshelf.ui.profile.ProfileScreen
import info.movieshelf.data.local.db.AppMode
import info.movieshelf.ui.setup.ModeChoiceScreen
import info.movieshelf.ui.setup.SetupScreen
import info.movieshelf.ui.stats.StatsScreen
import info.movieshelf.ui.sync.SyncScreen
import info.movieshelf.ui.theme.MovieShelfTheme
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
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
            val themeMode by dsm.themeMode.collectAsState(initial = ThemeMode.DARK)
            MovieShelfTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                },
                dynamicColor = dynamicColor
            ) {
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

/**
 * Route zur Detailseite.
 *
 * Navigiert wird über die lokale ID. Filme, die direkt aus dem Netz stammen
 * (Listen-Inhalte, Darsteller-Filmografie, Boxset-Kinder), haben noch keine —
 * für sie wird die Server-ID mitgegeben, damit die Detailseite die lokale Zeile
 * nachschlagen oder den Film nachladen kann.
 */
private fun movieDetailsRoute(movie: Movie, allLocalIds: List<Long> = emptyList()): String {
    val query = buildList {
        if (allLocalIds.isNotEmpty()) add("allIds=${allLocalIds.joinToString(",")}")
        if (movie.localId == 0L && movie.id != 0) add("remoteId=${movie.id}")
    }.joinToString("&")
    return "movie_details/${movie.localId}" + if (query.isEmpty()) "" else "?$query"
}

/**
 * Wie [movieDetailsRoute]: die lokale ID traegt den Weg, die Server-ID haengt
 * als Zusatz daran. Ohne lokale ID (Person nur auf dem Server bekannt) bleibt
 * der Pfad 0 und nur die Server-ID zaehlt.
 */
private fun actorDetailsRoute(actor: Actor): String {
    val query = if (actor.id != null) "?remoteId=${actor.id}" else ""
    return "actor_details/${actor.localId}$query"
}

@Composable
fun MovieShelfApp(oauthCallbackUri: MutableState<Uri?> = mutableStateOf(null)) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val app = context.applicationContext as MovieShelfApplication

    // Beide Quellen liefern erst verzoegert. Bis dahin ist ihr Wert unbekannt —
    // und "unbekannt" darf nicht wie "nicht eingerichtet" behandelt werden,
    // sonst blitzt die Einrichtungsseite auf, waehrend die Werte noch laden.
    var serverUrlLoaded by remember { mutableStateOf(false) }
    val serverUrl by remember {
        dataStoreManager.serverUrl.onEach { serverUrlLoaded = true }
    }.collectAsState(initial = null)

    var modeLoaded by remember { mutableStateOf(false) }
    // null = noch nicht gewaehlt, dann kommt die Moduswahl.
    val appMode by remember {
        app.appMode.onEach { modeLoaded = true }
    }.collectAsState(initial = null)

    val bootstrapping = !modeLoaded || !serverUrlLoaded
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showNavBar = currentRoute in listOf("dashboard", "profile", "stats")

    // Steuert das Ein-/Ausblenden der unteren NavBar beim Scrollen.
    var bottomBarVisible by remember { mutableStateOf(true) }

    // Umgeschaltet wird erst, wenn eine Strecke zurückgelegt ist — vorher genügte
    // ein einziges Pixel. Beim Lesen reicht dann schon das Nachfedern des Fingers
    // auf dem Display, und die Leiste klappt ungefragt wieder auf.
    //
    // Die Schwellen sind bewusst ungleich: Ausblenden darf früh geschehen, das
    // stört niemanden und gibt Platz. Einblenden verlangt eine bewusste Geste
    // nach oben, weil genau dort das Aufploppen lästig war.
    val density = LocalDensity.current
    val hideThreshold = remember(density) { with(density) { 12.dp.toPx() } }
    val showThreshold = remember(density) { with(density) { 56.dp.toPx() } }

    val nestedScrollConnection = remember(hideThreshold, showThreshold) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            /** Zurückgelegte Strecke seit dem letzten Richtungswechsel. */
            var travelled = 0f

            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                val dy = available.y
                if (dy == 0f) return androidx.compose.ui.geometry.Offset.Zero

                // Beim Richtungswechsel bei null anfangen. Ohne das zehrte eine
                // lange Bewegung nach unten die Schwelle nach oben auf, und der
                // erste Millimeter zurück würde wieder sofort umschalten.
                if ((dy < 0f) != (travelled < 0f)) travelled = 0f
                travelled += dy

                if (travelled <= -hideThreshold) {
                    bottomBarVisible = false
                    travelled = 0f
                } else if (travelled >= showThreshold) {
                    bottomBarVisible = true
                    travelled = 0f
                }
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

    LaunchedEffect(serverUrl, appMode, bootstrapping) {
        // Solange die Werte nicht feststehen, nichts entscheiden.
        if (bootstrapping) return@LaunchedEffect

        // Eigenstaendig: kein Server, kein Token, direkt in die Sammlung.
        if (appMode == AppMode.STANDALONE) {
            startDestination = "dashboard"
            isInitialized = true
            initializationError = false
            isLoadingAuth = false
            return@LaunchedEffect
        }
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

    if (bootstrapping) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (appMode == null) {
        ModeChoiceScreen(onModeChosen = { chosen ->
            scope.launch { app.setAppMode(chosen) }
        })
    } else if (appMode == AppMode.SHELF && (serverUrl.isNullOrBlank() || initializationError)) {
        SetupScreen(
            dataStoreManager = dataStoreManager,
            onSetupComplete = { initializationError = false },
            onChangeMode = { scope.launch { app.clearAppMode() } }
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
                        onChangeMode = { scope.launch { app.clearAppMode() } },
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
                        isShelfMode = appMode == AppMode.SHELF,
                        onSyncClick = { navController.navigate("sync") },
                        onMovieClick = { movie: Movie, allIds: List<Long> ->
                            navController.navigate(movieDetailsRoute(movie, allIds))
                        }
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        onBack = { navController.popBackStack() },
                        onListsClick = { navController.navigate("lists") },
                        onTwoFactorClick = { navController.navigate("twofactor") },
                        onJellyfinClick = { navController.navigate("jellyfin") },
                        onWishlistClick = { navController.navigate("wishlist") },
                        onAccessClick = { navController.navigate("access") },
                        onSyncClick = { navController.navigate("sync") },
                        isStandalone = appMode == AppMode.STANDALONE,
                        onConnectShelfClick = {
                            scope.launch {
                                app.setAppMode(AppMode.SHELF)
                            }
                        },
                        onAboutClick = { navController.navigate("about") }
                    )
                }
                composable("sync") {
                    SyncScreen(onBack = { navController.popBackStack() })
                }
                composable("twofactor") {
                    TwoFactorScreen(onBack = { navController.popBackStack() })
                }
                composable("access") {
                    info.movieshelf.ui.access.AccessScreen(onBack = { navController.popBackStack() })
                }
                composable("wishlist") {
                    info.movieshelf.ui.wishlist.WishlistScreen(
                        onBack = { navController.popBackStack() },
                        onMovieClick = { movie ->
                            navController.navigate(movieDetailsRoute(movie))
                        }
                    )
                }
                composable("jellyfin") {
                    info.movieshelf.ui.jellyfin.JellyfinScreen(
                        onBack = { navController.popBackStack() },
                        isShelfMode = appMode != AppMode.STANDALONE
                    )
                }
                composable("create_movie") {
                    CreateMovieScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { newLocalId ->
                            navController.navigate("movie_details/$newLocalId") {
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
                        onMovieClick = { movie: Movie -> navController.navigate(movieDetailsRoute(movie)) }
                    )
                }
                composable(
                    "movie_details/{localId}?allIds={allIds}&remoteId={remoteId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("localId") { type = androidx.navigation.NavType.LongType },
                        androidx.navigation.navArgument("allIds") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        androidx.navigation.navArgument("remoteId") {
                            type = androidx.navigation.NavType.IntType
                            defaultValue = 0
                        }
                    )
                ) { backStackEntry ->
                    val localId = backStackEntry.arguments?.getLong("localId") ?: 0L
                    val remoteId = backStackEntry.arguments?.getInt("remoteId") ?: 0
                    val allIdsString = backStackEntry.arguments?.getString("allIds")
                    val allMovieIds = allIdsString?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
                    // Signal vom Edit-Screen: hochgezählt, sobald ein Film bearbeitet wurde
                    val reloadKey by backStackEntry.savedStateHandle
                        .getStateFlow("movie_edited", 0)
                        .collectAsState()

                    MovieDetailScreen(
                        movieLocalId = localId,
                        movieRemoteId = remoteId,
                        allMovieIds = allMovieIds,
                        reloadKey = reloadKey,
                        onBack = { navController.popBackStack() },
                        onEditClick = { editLocalId -> navController.navigate("edit_movie/$editLocalId") },
                        onMovieClick = { movie: Movie ->
                            navController.navigate(movieDetailsRoute(movie))
                        },
                        onActorClick = { actor ->
                            navController.navigate(actorDetailsRoute(actor))
                        },
                        // Ein Name aus dem Beschreibungstext, ohne Eintrag in
                        // der Besetzung. Erst lokal nachsehen — der Umweg ueber
                        // die Serversuche war der Grund, warum diese Verweise
                        // ohne Netz ins Leere liefen.
                        onActorNameClick = { actorName ->
                            scope.launch {
                                val localId = runCatching {
                                    app.actorRepository.findLocalIdByName(actorName)
                                }.getOrNull()
                                if (localId != null) {
                                    navController.navigate("actor_details/$localId")
                                    return@launch
                                }
                                try {
                                    val remoteId = app.actorRepository.findRemoteIdByName(actorName)
                                    if (remoteId != null) {
                                        navController.navigate("actor_details/0?remoteId=$remoteId")
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
                composable("actor_details/{localId}?remoteId={remoteId}") { backStackEntry ->
                    val localId = backStackEntry.arguments?.getString("localId")?.toLongOrNull() ?: 0L
                    val remoteId = backStackEntry.arguments?.getString("remoteId")?.toIntOrNull()
                    ActorDetailScreen(
                        localId = localId,
                        remoteId = remoteId,
                        onBack = { navController.popBackStack() },
                        onMovieClick = { movie: Movie ->
                            navController.navigate(movieDetailsRoute(movie))
                        }
                    )
                }
                composable("add_movie") {
                    AddMovieScreen(
                        onBack = { navController.popBackStack() },
                        // Nach dem Import direkt in die Bearbeitung: TMDb liefert
                        // nicht alles, was am Ende dranstehen soll - Format, Standort
                        // im Regal, Kaufdatum. Ohne diesen Sprung muesste man den
                        // frisch angelegten Film erst in der Sammlung wiederfinden.
                        // Die Suche verlaesst der Weg in jedem Fall, sie ist nach
                        // einem Treffer ohnehin zu Ende.
                        onMovieImported = { localId ->
                            navController.popBackStack()
                            if (localId != null) navController.navigate("edit_movie/$localId")
                        },
                        onCreateManual = { navController.navigate("create_movie") }
                    )
                }
                composable(
                    "edit_movie/{localId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("localId") { type = androidx.navigation.NavType.LongType }
                    )
                ) { backStackEntry ->
                    val localId = backStackEntry.arguments?.getLong("localId") ?: 0L
                    EditMovieScreen(
                        movieLocalId = localId,
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
                    showLogout = appMode == AppMode.SHELF,
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
                    title = { Text(stringResource(R.string.logout_question)) },
                    text = { Text(stringResource(R.string.logout_body)) },
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
                            Text(stringResource(R.string.nav_logout))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }
        }
    }
}
