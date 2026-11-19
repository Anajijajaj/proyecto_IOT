package com.example.myapp
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppControlAccesoIoT()
                }
            }
        }
    }
}

@Composable
fun AppControlAccesoIoT() {
    val context = LocalContext.current
    val db = remember { DatabaseHelper(context) }

    var usuarioLogueado by remember { mutableStateOf<String?>(null) }
    var rolLogueado by remember { mutableStateOf<String?>(null) }

    if (usuarioLogueado == null) {
        PantallaLogin(
            db = db,
            onLoginExitoso = { user, rol ->
                usuarioLogueado = user
                rolLogueado = rol
            }
        )
    } else {
        PantallaDashboard(
            db = db,
            usuario = usuarioLogueado!!,
            rol = rolLogueado!!,
            onCerrarSesion = {
                Log.i("LOGIN_ACCESO", "Sesión cerrada por $usuarioLogueado")
                usuarioLogueado = null
                rolLogueado = null
            }
        )
    }
}

@Composable
fun PantallaLogin(db: DatabaseHelper, onLoginExitoso: (String, String) -> Unit) {
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var intentosFallidos by remember { mutableIntStateOf(0) }
    var mensajeError by remember { mutableStateOf("") }
    val maxIntentos = 3
    val bloqueado = intentosFallidos >= maxIntentos

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Smart Lock IoT",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Control de Acceso con Arduino (ISO 27400)",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Usuario (admin o operador)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                val u = usuario.trim()
                val p = password.trim()
                if (u.isEmpty() || p.isEmpty()) {
                    mensajeError = "Ingrese usuario y contraseña."
                    return@Button
                }
                val rol = db.validarLogin(u, p)
                if (rol != null) {
                    intentosFallidos = 0
                    mensajeError = ""
                    Log.i("LOGIN_ACCESO", "Acceso autorizado a '$u' con rol: $rol")
                    onLoginExitoso(u, rol)
                } else {
                    intentosFallidos++
                    val restantes = maxIntentos - intentosFallidos
                    Log.w("LOGIN_ACCESO", "Intento fallido ($intentosFallidos/$maxIntentos) para '$u'")
                    if (intentosFallidos >= maxIntentos) {
                        mensajeError = "BLOQUEADO: Superó el máximo de 3 intentos fallidos."
                        Log.e("SEGURIDAD_ISO27400", "Bloqueo activado por fuerza bruta en login.")
                    } else {
                        mensajeError = "Credenciales inválidas. Intentos restantes: $restantes"
                    }
                }
            },
            enabled = !bloqueado,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (bloqueado) "Acceso Bloqueado" else "Iniciar Sesión")
        }

        if (mensajeError.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = mensajeError,
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PantallaDashboard(
    db: DatabaseHelper,
    usuario: String,
    rol: String,
    onCerrarSesion: () -> Unit
) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }

    var conectado by remember { mutableStateOf(true) }
    var estadoBluetooth by remember { mutableStateOf("Bluetooth HC-05 (9600 bps): CONECTADO") }
    var estadoCerrojo by remember { mutableStateOf("CERROJO SG90: BLOQUEADO (0°)") }
    var cerrojoAbierto by remember { mutableStateOf(false) }
    var historial by remember { mutableStateOf(db.obtenerHistorial()) }

    var nuevoUser by remember { mutableStateOf("") }
    var nuevoPass by remember { mutableStateOf("") }

    fun horaActual(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    fun ejecutarApertura(origen: String, detalle: String) {
        cerrojoAbierto = true
        estadoCerrojo = "CERROJO SG90: ABIERTO (90°)"
        db.registrarEvento(origen, detalle, "ABIERTO (90°)", horaActual())
        historial = db.obtenerHistorial()
        Log.i("ARDUINO_UART", "[ESTADO]: Cerrojo ABIERTO (90°) por [$origen] - $detalle")

        // Temporizador de 3 segundos igual al código de Arduino
        handler.postDelayed({
            cerrojoAbierto = false
            estadoCerrojo = "CERROJO SG90: BLOQUEADO (0°)"
            db.registrarEvento("AUTO-CIERRE", "Temporizador 3s completado", "BLOQUEADO (0°)", horaActual())
            historial = db.obtenerHistorial()
            Log.i("ARDUINO_UART", "[ESTADO]: Cerrojo BLOQUEADO (0°)")
        }, 3000)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Usuario: $usuario ($rol)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = estadoBluetooth,
            color = if (conectado) Color(0xFF2E7D32) else Color(0xFFD32F2F),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (cerrojoAbierto) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Text(
                text = estadoCerrojo,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (cerrojoAbierto) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                modifier = Modifier.padding(16.dp)
            )
        }

        Button(
            onClick = {
                if (!conectado) {
                    Toast.makeText(context, "Sin enlace Bluetooth. Esperando reconexión...", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                Log.i("BLUETOOTH_SPP", "TX -> 'O' | [REMOTO]: Comando APERTURA enviado por $usuario")
                ejecutarApertura("REMOTO BT ('O')", "Apertura por $usuario")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abrir Puerta Remoto (Comando 'O')")
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = {
                if (!conectado) return@Button
                cerrojoAbierto = false
                estadoCerrojo = "CERROJO SG90: BLOQUEADO (0°)"
                db.registrarEvento("REMOTO BT ('C')", "Cierre forzado por $usuario", "BLOQUEADO (0°)", horaActual())
                historial = db.obtenerHistorial()
                Log.i("BLUETOOTH_SPP", "TX -> 'C' | [REMOTO]: Comando CIERRE forzado recibido")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar Puerta Forzado (Comando 'C')")
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = {
                if (!conectado) return@OutlinedButton
                Log.i("ARDUINO_UART", "RX <- [RFID]: Tarjeta detectada UID: 4A 8F 12 9B -> AUTORIZADO")
                ejecutarApertura("RFID-RC522", "UID 4A 8F 12 9B AUTORIZADO")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simular Lectura RFID Autorizada")
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = {
                if (!conectado) return@OutlinedButton
                cerrojoAbierto = false
                estadoCerrojo = "CERROJO SG90: BLOQUEADO (0°) - ALERTA"
                db.registrarEvento("ALERTA RFID", "UID FF 00 11 22 DENEGADO", "BLOQUEADO (0°)", horaActual())
                historial = db.obtenerHistorial()
                Log.e("ARDUINO_UART", "RX <- [ALERTA]: DENEGADO -> Tarjeta no autorizada (Buzzer activo)")
                Toast.makeText(context, "ALERTA: Tarjeta RFID no autorizada", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simular Tarjeta RFID Denegada (Alerta)")
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = {
                conectado = false
                cerrojoAbierto = false
                estadoBluetooth = "Bluetooth HC-05: DESCONECTADO (Fail-Secure 0°) - Reconectando..."
                estadoCerrojo = "CERROJO SG90: BLOQUEADO (0°)"
                Log.w("FAILSAFE_IOT", "Enlace Bluetooth perdido. Cerrojo asegurado en 0°. Reintentando en 2s...")

                handler.postDelayed({
                    conectado = true
                    estadoBluetooth = "Bluetooth HC-05 (9600 bps): RECONECTADO"
                    Log.i("FAILSAFE_IOT", "Reconexión Bluetooth HC-05 exitosa tras 2000ms.")
                    Toast.makeText(context, "Notificación IoT: Conexión HC-05 restablecida", Toast.LENGTH_LONG).show()
                }, 2000)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simular Corte Bluetooth / Reconexión (2s)")
        }

        // Panel exclusivo para Administrador
        if (rol == "ADMIN") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Panel Exclusivo Administrador",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = nuevoUser,
                    onValueChange = { nuevoUser = it },
                    label = { Text("Nuevo usuario operador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = nuevoPass,
                    onValueChange = { nuevoPass = it },
                    label = { Text("Contraseña (mín. 8 caracteres)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val u = nuevoUser.trim()
                        val p = nuevoPass.trim()
                        if (u.isEmpty() || p.length < 8) {
                            Toast.makeText(context, "Usuario válido y contraseña de mínimo 8 caracteres", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (db.crearUsuario(u, p, "USER")) {
                            Toast.makeText(context, "Operador '$u' creado correctamente", Toast.LENGTH_SHORT).show()
                            nuevoUser = ""
                            nuevoPass = ""
                        } else {
                            Toast.makeText(context, "El usuario ya existe", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Crear Usuario")
                }
                TextButton(
                    onClick = {
                        db.limpiarHistorial()
                        historial = db.obtenerHistorial()
                        Toast.makeText(context, "Auditoría SQLite limpiada", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Limpiar Auditoría SQLite")
                }
            }
        }

        Text(
            text = "Registro Telemétrico SQLite (Tiempo Real):",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        Text(
            text = historial,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCerrarSesion,
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar Sesión")
        }
    }
}