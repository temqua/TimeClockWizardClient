package io.github.temqua.timeclockwizardclient


import android.content.Context.CONNECTIVITY_SERVICE
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.os.Bundle
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userAgent = WebSettings.getDefaultUserAgent(this)
        setContent {
            Main()
        }
    }
}

var userAgent: String = ""

@Preview(name = "Light Mode")
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    name = "Dark Mode"
)
@Composable
fun Main() {
    MainTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("TimeClockWizard Client") },
                    /*
                                        actions = {
                                            IconButton(onClick = {}) {
                                                Icon(Icons.Filled.MoreVert, "More")
                                            }
                                        }
                    */
                )
            },
            content = {
                Content(it)
            }
        )
    }
}

@Composable
fun Content(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val dataStore = Store(context)
    val composableScope = rememberCoroutineScope()
    val savedEmail = dataStore.emailFlow.collectAsState(initial = "")
    val savedSubdomain = dataStore.subdomainFlow.collectAsState(initial = "")
    val snackbarHostState = remember {
        SnackbarHostState()
    }
    var updatedEmail by remember { mutableStateOf(false) }
    var updatedSubdomain by remember { mutableStateOf(false) }
    var emailState by remember { mutableStateOf(savedEmail.value) }
    var passwordState by remember { mutableStateOf("") }
    var subDomainState by remember { mutableStateOf(savedSubdomain.value) }
    var commandState by remember { mutableStateOf(TimerCommand.ClockIn) }
    var expanded by remember { mutableStateOf(false) }
    var passwordVisibility by remember { mutableStateOf(false) }

    if (savedEmail.value.isNotEmpty() && !updatedEmail) {
        emailState = savedEmail.value
        updatedEmail = true
    }
    if (savedSubdomain.value.isNotEmpty() && !updatedSubdomain) {
        subDomainState = savedSubdomain.value
        updatedSubdomain = true
    }
    val icon = if (passwordVisibility)
        painterResource(id = R.drawable.baseline_visibility_off_24)
    else
        painterResource(id = R.drawable.baseline_visibility_24)
    var colSize by remember { mutableStateOf(Size.Zero) }
    Column(
        modifier = Modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally

    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = emailState,
            onValueChange = { emailState = it.trim() },
            label = { Text(text = "Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = passwordState,
            onValueChange = { passwordState = it.trim() },
            label = { Text(text = "Password") },
            trailingIcon = {
                IconButton(onClick = {
                    passwordVisibility = !passwordVisibility
                }) {
                    Icon(
                        painter = icon,
                        contentDescription = "Visibility Icon"
                    )
                }
            },
            visualTransformation = if (passwordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = subDomainState,
            onValueChange = { subDomainState = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Subdomain") },

            )
        Spacer(modifier = Modifier.height(16.dp))
        IconButton(
            modifier = Modifier
                .background(MaterialTheme.colors.background)
                .border(ButtonDefaults.outlinedBorder)
                .fillMaxWidth(),
            interactionSource = NoRippleInteractionSource(),
            onClick = {
                expanded = true
            },
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp)
            ) {
                Text(
                    text = commandState.command,
                    color = MaterialTheme.colors.primary
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose command")
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    onClick = {
                        commandState = TimerCommand.ClockIn
                        expanded = false
                    },
                    content = { Text(text = "Clock In") }
                )
                DropdownMenuItem(
                    onClick = {
                        commandState = TimerCommand.ClockOut
                        expanded = false
                    },
                    content = { Text(text = "Clock Out") }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                composableScope.launch {
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailState).matches()) {
                        snackbarHostState.showSnackbar(
                            message = "You entered invalid email",
                        )
                        return@launch
                    }
                    if (subDomainState.isEmpty()) {
                        snackbarHostState.showSnackbar(
                            message = "Subdomain field must contain data",
                        )
                        return@launch
                    }
                    if (passwordState.isEmpty()) {
                        snackbarHostState.showSnackbar(
                            message = "Password field must contain data",
                        )
                        return@launch
                    }
                    val connManager =
                        context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    val networkCapabilities =
                        connManager.getNetworkCapabilities(connManager.activeNetwork)
                    if (networkCapabilities == null) {
                        snackbarHostState.showSnackbar(
                            message = "Please check your internet connection. Turn on Wi-Fi or mobile network.",
                        )
                        return@launch
                    }
                    withContext(Dispatchers.IO) {
                        dataStore.saveEmail(emailState)
                        dataStore.saveSubdomain(subDomainState)
                        val result = fetchVerificationToken(userAgent, subDomainState)
                        if (!result.successful || result.data.isEmpty()) {
                            snackbarHostState.showSnackbar(
                                message = "Authorization unsuccessful. Check your credentials and internet connection please. ${result.error}",
                            )
                            return@withContext
                        }
                        val cookies = result.data
                        val cookieList = cookies.split(";")
                        val verificationCookie =
                            cookieList.find { cookie -> cookie.contains("__RequestVerificationToken") }
                        val verificationToken = verificationCookie?.split("=")?.get(1) ?: ""
                        val submitResult = submitForm(
                            userAgent,
                            emailState,
                            passwordState,
                            subDomainState,
                            commandState.toString(),
                            verificationToken,
                            cookies
                        )
                        val msg =
                            if (submitResult) "You have been successfully ${getMessage(commandState)}"
                            else "Authorization unsuccessful. Check your credentials, please."

                        snackbarHostState.showSnackbar(
                            message = msg,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "SUBMIT")
        }
        SnackbarHost(
            hostState = snackbarHostState,
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                )
            },
        )
    }
}

fun getMessage(command: TimerCommand): String =
    if (command == TimerCommand.ClockIn) "clocked in" else "clocked out"


fun fetchVerificationToken(userAgent: String, subDomain: String): Result {
    val client = OkHttpClient()
    val loginRequest = Request.Builder()
        .url("https://apps.timeclockwizard.com/Login?subDomain=$subDomain")
        .addHeader("User-Agent", userAgent)
        .build()
    val loginResponse = client.newCall(loginRequest).execute()
    val cookies = loginResponse.headers.values("set-cookie")
    if (!loginResponse.isSuccessful) {
        return Result(false, "", loginResponse.message)
    }
    val handledCookies = cookies.map { cookie -> cookie.split(";")[0] }.joinToString("; ")
    return Result(true, handledCookies, "")
}

data class Result(
    val successful: Boolean,
    val data: String,
    val error: String
)

fun submitForm(
    userAgent: String,
    email: String,
    password: String,
    subDomain: String,
    command: String,
    token: String,
    cookies: String
): Boolean {
    val client = OkHttpClient()
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("__RequestVerificationToken", token)
        .addFormDataPart("Subdomain", subDomain)
        .addFormDataPart("ClientDetails.QuickClockInPassword", "True")
        .addFormDataPart("ClientDetails.QuickClockIn", "True")
        .addFormDataPart("UserName", email)
        .addFormDataPart("Password", password)
        .addFormDataPart("command", command)
        .build()
    val request = Request.Builder()
        .url("https://apps.timeclockwizard.com/Login")
        .addHeader("User-Agent", userAgent)
        .addHeader("Cookie", cookies)
        .post(requestBody)
        .build()
    val response = client.newCall(request).execute()
    val receivedCookies = response.headers.values("set-cookie")
    return response.isSuccessful && receivedCookies.isNotEmpty()
}

class NoRippleInteractionSource : MutableInteractionSource {

    override val interactions: Flow<Interaction> = emptyFlow()

    override suspend fun emit(interaction: Interaction) {}

    override fun tryEmit(interaction: Interaction) = true
}