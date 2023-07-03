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
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
    var emailState by remember { mutableStateOf("") }
    var passwordState by remember { mutableStateOf("") }
    var subDomainState by remember { mutableStateOf("") }
    var commandState by remember { mutableStateOf(TimerCommand.ClockIn) }
    val composableScope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val (snackbarVisibleState, setSnackBarState) = remember { mutableStateOf(false) }
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = subDomainState,
            onValueChange = { subDomainState = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "SubDomain") },
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
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                composableScope.launch {
                    withContext(Dispatchers.IO) {
                        val result = fetchVerificationToken(userAgent, subDomainState)
                        if (result.successful && result.data.isNotEmpty()) {
                            val submitResult = submitForm(
                                userAgent,
                                emailState,
                                passwordState,
                                subDomainState,
                                commandState.toString(),
                                result.data
                            )
                            if (submitResult.successful) {
                                scaffoldState.snackbarHostState.showSnackbar(
                                    message = "You have been successfully authorized",
                                )
                            } else {
                                scaffoldState.snackbarHostState.showSnackbar(
                                    message = "Authorization unsuccessful. Check your credentials, please.",
                                )
                            }
                        } else {
                            scaffoldState.snackbarHostState.showSnackbar(
                                message = "Authorization unsuccessful. Check your internet connection, please.",
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "SUBMIT")
        }
    }
}

fun fetchVerificationToken(userAgent: String, subDomain: String): Result {
    val client = OkHttpClient()
    val loginRequest = Request.Builder()
        .url("https://apps.timeclockwizard.com/Login?subDomain=$subDomain")
        .addHeader("User-Agent", userAgent)
        .build()
    val loginResponse = client.newCall(loginRequest).execute()
    if (!loginResponse.isSuccessful) {
        return Result(false, "")
    }
    val responseBody = loginResponse.body ?: return Result(false, "")
    val resp = responseBody.string()
    val reg = Regex("""<input name="__RequestVerificationToken" type="hidden" value="([^"]+)" />""")
    val (token) = reg.find(resp)?.destructured ?: return Result(false, "")
    return Result(true, token)
}

data class Result(
    val successful: Boolean,
    val data: String,
)

fun submitForm(
    userAgent: String,
    email: String,
    password: String,
    subDomain: String,
    command: String,
    token: String
): Result {
    val client = OkHttpClient()
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("__RequestVerificationToken", token)
        .addFormDataPart("Subdomain", subDomain)
        .addFormDataPart("ClientDetails.QuickClockInPassword", "true")
        .addFormDataPart("ClientDetails.QuickClockIn", command)
        .addFormDataPart("UserName", email)
        .addFormDataPart("Password", password)
        .addFormDataPart("command", "LogIn")
        .build()
    val request = Request.Builder()
        .url("https://apps.timeclockwizard.com/Login")
        .post(requestBody)
        .build()
    val response = client.newCall(request).execute()
    return Result(response.isSuccessful, "")

}