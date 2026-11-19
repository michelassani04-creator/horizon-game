package com.horizon.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.auth.AuthViewModel
import com.horizon.game.ui.components.OsPage
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonRed
import com.horizon.game.ui.theme.HorizonSurface

/** Inscription et connexion HORIZON ID (e-mail ou téléphone). */
@Composable
fun AuthScreen(
    auth: AuthViewModel,
    onDone: () -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var register by rememberSaveable { mutableStateOf(false) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { auth.clearMessages() } }

    val canSubmit = !auth.busy && password.length >= 8 && if (register) {
        name.trim().length >= 2 && (email.isNotBlank() || phone.isNotBlank())
    } else {
        identifier.isNotBlank()
    }

    OsPage(title = "HORIZON ID", onBack = onDone, modifier = modifier) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                Modifier.weight(0.38f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = HorizonBlue, modifier = Modifier.size(56.dp))
                Text("Ton identité de joueur", style = MaterialTheme.typography.titleLarge, color = HorizonOnSurface, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Un seul compte pour ton profil, tes favoris et tes préférences, sur tous tes appareils.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HorizonOnSurfaceMuted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Column(
                Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
                    .background(HorizonSurface, RoundedCornerShape(20.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { register = false; auth.clearMessages() }) {
                        Text("Connexion", color = if (!register) HorizonBlue else HorizonOnSurfaceMuted)
                    }
                    TextButton(onClick = { register = true; auth.clearMessages() }) {
                        Text("Inscription", color = if (register) HorizonBlue else HorizonOnSurfaceMuted)
                    }
                }
                if (register) {
                    Field("Pseudo", name, { name = it })
                    Field("Adresse e-mail", email, { email = it }, KeyboardType.Email)
                    Field("Téléphone (+243…, optionnel si e-mail)", phone, { phone = it }, KeyboardType.Phone)
                } else {
                    Field("E-mail ou téléphone", identifier, { identifier = it }, KeyboardType.Email)
                }
                Field("Mot de passe (8 caractères minimum)", password, { password = it }, KeyboardType.Password, secret = true)

                auth.error?.let { Text(it, color = HorizonRed, style = MaterialTheme.typography.bodyMedium) }
                auth.info?.let { Text(it, color = HorizonBlue, style = MaterialTheme.typography.bodyMedium) }

                Button(
                    enabled = canSubmit,
                    onClick = {
                        if (register) auth.register(email, phone, password, name, onDone)
                        else auth.login(identifier, password, onDone)
                    }
                ) { Text(if (auth.busy) "Patiente…" else if (register) "Créer mon HORIZON ID" else "Se connecter") }

                if (auth.serverUrl.isBlank()) {
                    TextButton(onClick = onOpenServerSettings) { Text("Serveur non configuré : ouvrir les réglages") }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}
