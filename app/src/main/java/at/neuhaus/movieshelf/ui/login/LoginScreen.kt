package at.neuhaus.movieshelf.ui.login

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.neuhaus.movieshelf.R
import at.neuhaus.movieshelf.data.local.DataStoreManager

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onResetUrl: () -> Unit,
    oauthCallbackUri: Uri? = null,
    onOAuthCallbackConsumed: () -> Unit = {}
) {
    val viewModel: LoginViewModel = viewModel()
    val oauthViewModel: OAuthViewModel = viewModel()
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val serverUrl by dataStoreManager.serverUrl.collectAsState(initial = "")

    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.loginSuccess) {
        if (viewModel.loginSuccess) onLoginSuccess()
    }

    LaunchedEffect(oauthViewModel.loginSuccess) {
        if (oauthViewModel.loginSuccess) onLoginSuccess()
    }

    LaunchedEffect(oauthCallbackUri) {
        if (oauthCallbackUri != null) {
            oauthViewModel.handleCallback(oauthCallbackUri, serverUrl ?: "", dataStoreManager)
            onOAuthCallbackConsumed()
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "MovieShelf Logo",
                    modifier = Modifier
                        .height(100.dp)
                        .padding(bottom = 32.dp)
                )

                Text(
                    text = if (viewModel.is2faRequired) "Zwei-Faktor-Check" else "Anmelden",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (!viewModel.is2faRequired) {
                    Card(
                        modifier = Modifier.padding(bottom = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Beta-Test / Demo-Zugang",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    text = "E-Mail: demo@movieshelf.info\nPasswort: playstore",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                }

                AnimatedContent(targetState = viewModel.is2faRequired, label = "LoginMode") { is2fa ->
                    if (is2fa) {
                        Column {
                            Text(
                                "Bitte gib den 6-stelligen Code aus deiner Authenticator-App ein.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            OutlinedTextField(
                                value = viewModel.code2fa,
                                onValueChange = { if (it.length <= 6) viewModel.code2fa = it },
                                label = { Text("2FA Code") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true
                            )
                        }
                    } else {
                        Column {
                            OutlinedTextField(
                                value = viewModel.email,
                                onValueChange = { viewModel.email = it },
                                label = { Text("E-Mail") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true
                            )

                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = viewModel.password,
                                onValueChange = { viewModel.password = it },
                                label = { Text("Passwort") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    val icon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                    val description = if (passwordVisible) "Passwort ausblenden" else "Passwort anzeigen"
                                    
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = icon, contentDescription = description)
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true
                            )
                        }
                    }
                }

                if (oauthViewModel.error != null) {
                    Text(
                        text = oauthViewModel.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (viewModel.error != null) {
                    Text(
                        text = viewModel.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(Modifier.height(32.dp))

                // OAuth-Login mit Movieshelf Cloud
                if (!viewModel.is2faRequired) {
                    Button(
                        onClick = { oauthViewModel.startOAuth(context, serverUrl ?: "", dataStoreManager) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !oauthViewModel.isLoading && !viewModel.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        if (oauthViewModel.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSecondary)
                        } else {
                            Icon(Icons.Default.Cloud, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Mit Movieshelf anmelden")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "  oder  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                }

                Button(
                    onClick = {
                        if (viewModel.is2faRequired) {
                            viewModel.onVerify2faClick(dataStoreManager)
                        } else {
                            viewModel.onLoginClick(dataStoreManager)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !viewModel.isLoading && !oauthViewModel.isLoading
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        val icon = if (viewModel.is2faRequired) Icons.Default.Pin else Icons.AutoMirrored.Filled.Login
                        Icon(icon, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (viewModel.is2faRequired) "Code verifizieren" else "Anmelden")
                    }
                }

                TextButton(
                    onClick = onResetUrl,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Server-URL ändern")
                }
            }
        }
    }
}
