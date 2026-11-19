package com.example.myapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.security.MessageDigest

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "ControlAccesoIoT.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE usuarios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                rol TEXT NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE eventos_acceso (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                origen TEXT NOT NULL,
                detalle TEXT NOT NULL,
                estado_cerrojo TEXT NOT NULL,
                fecha TEXT NOT NULL
            )
        """)

        // Usuarios iniciales precargados para la demostración
        insertarUsuarioInterno(db, "admin", "Admin#2026", "ADMIN")
        insertarUsuarioInterno(db, "operador", "Oper#2026", "USER")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        db.execSQL("DROP TABLE IF EXISTS eventos_acceso")
        onCreate(db)
    }

    private fun hashConSalt(password: String, salt: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((password + salt).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun insertarUsuarioInterno(db: SQLiteDatabase, user: String, pass: String, rol: String) {
        val salt = "SALT_TI3042_${user.uppercase()}"
        val cv = ContentValues().apply {
            put("username", user)
            put("password_hash", hashConSalt(pass, salt))
            put("salt", salt)
            put("rol", rol)
        }
        db.insert("usuarios", null, cv)
    }

    fun crearUsuario(user: String, pass: String, rol: String): Boolean {
        val db = writableDatabase
        val salt = "SALT_TI3042_${System.currentTimeMillis()}"
        val cv = ContentValues().apply {
            put("username", user)
            put("password_hash", hashConSalt(pass, salt))
            put("salt", salt)
            put("rol", rol)
        }
        val res = db.insert("usuarios", null, cv)
        Log.i("SEGURIDAD_ISO27400", "Nuevo usuario '$user' (rol=$rol) creado con SHA-256+Salt -> ok=${res != -1L}")
        return res != -1L
    }

    fun validarLogin(user: String, pass: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT password_hash, salt, rol FROM usuarios WHERE username = ?", arrayOf(user))
        var rolValidado: String? = null
        if (cursor.moveToFirst()) {
            val hashGuardado = cursor.getString(0)
            val salt = cursor.getString(1)
            val rol = cursor.getString(2)
            if (hashConSalt(pass, salt) == hashGuardado) {
                rolValidado = rol
            }
        }
        cursor.close()
        return rolValidado
    }

    fun registrarEvento(origen: String, detalle: String, estadoCerrojo: String, fecha: String) {
        val cv = ContentValues().apply {
            put("origen", origen)
            put("detalle", detalle)
            put("estado_cerrojo", estadoCerrojo)
            put("fecha", fecha)
        }
        writableDatabase.insert("eventos_acceso", null, cv)
    }

    fun obtenerHistorial(): String {
        val cursor = readableDatabase.rawQuery(
            "SELECT origen, detalle, estado_cerrojo, fecha FROM eventos_acceso ORDER BY id DESC LIMIT 6",
            null
        )
        val sb = StringBuilder()
        while (cursor.moveToNext()) {
            val origen = cursor.getString(0)
            val detalle = cursor.getString(1)
            val estado = cursor.getString(2)
            val fecha = cursor.getString(3)
            sb.append("• [$fecha] [$origen] $detalle -> $estado\n")
        }
        cursor.close()
        return if (sb.isEmpty()) "Sin eventos de acceso registrados." else sb.toString()
    }

    fun limpiarHistorial() {
        writableDatabase.execSQL("DELETE FROM eventos_acceso")
        Log.w("AUDITORIA_ADMIN", "Historial de accesos eliminado por el Administrador")
    }
}