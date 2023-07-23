package io.github.temqua.timeclockwizardclient


import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarHost
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
lateinit var scaffoldState: ScaffoldState

@Preview(name = "Light Mode")
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    name = "Dark Mode"
)
@Composable
fun Main() {
    MainTheme {
        scaffoldState = rememberScaffoldState()
        Scaffold(
            scaffoldState = scaffoldState,
            snackbarHost = {
                SnackbarHost(
                    hostState = it,
                    snackbar = { data ->
                        Snackbar(
                            snackbarData = data
                        )
                    },
                )
            },
            topBar = {
                TopAppBar(
                    title = { Text("TimeClockWizard Client") },
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
    var emailState by remember { mutableStateOf("") }
    var passwordState by remember { mutableStateOf("") }
    var subDomainState by remember { mutableStateOf("") }
    var commandState by remember { mutableStateOf(TimerCommand.ClockIn) }
    var expanded by remember { mutableStateOf(false) }
    var passwordVisibility by remember { mutableStateOf(false) }
    if (savedEmail.value.isNotEmpty()) {
        emailState = savedEmail.value
    }
    if (savedSubdomain.value.isNotEmpty()) {
        subDomainState = savedSubdomain.value
    }
    val icon = if (passwordVisibility)
        painterResource(id = R.drawable.baseline_visibility_off_24)
    else
        painterResource(id = R.drawable.baseline_visibility_24)
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
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                expanded = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(commandState.command)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
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
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = {
                composableScope.launch {
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailState).matches()) {
                        scaffoldState.snackbarHostState.showSnackbar(
                            message = "You entered invalid email",
                        )
                        return@launch
                    }
                    if (subDomainState.isEmpty()) {
                        scaffoldState.snackbarHostState.showSnackbar(
                            message = "Subdomain field must contain data",
                        )
                        return@launch
                    }
                    if (passwordState.isEmpty()) {
                        scaffoldState.snackbarHostState.showSnackbar(
                            message = "Password field must contain data",
                        )
                        return@launch
                    }
                    withContext(Dispatchers.IO) {
                        dataStore.saveEmail(emailState)
                        dataStore.saveSubdomain(subDomainState)
                        val result = fetchVerificationToken(userAgent, subDomainState)
                        if (!result.successful || result.data.isEmpty()) {
                            scaffoldState.snackbarHostState.showSnackbar(
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

                        scaffoldState.snackbarHostState.showSnackbar(
                            message = msg,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "SUBMIT")
        }
    }
}

fun getMessage(command: TimerCommand): String {
    return if (command == TimerCommand.ClockIn) "clocked in" else "clocked out"
}

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
