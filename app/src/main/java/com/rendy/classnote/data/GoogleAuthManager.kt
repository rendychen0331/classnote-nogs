package com.rendy.classnote.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

object GoogleAuthManager {

    private fun buildOptions() = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    fun getSignInIntent(context: Context): Intent =
        GoogleSignIn.getClient(context, buildOptions()).signInIntent

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            task.getResult(ApiException::class.java)
        } catch (_: ApiException) {
            null
        }
    }

    fun getAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun signOut(context: Context, onDone: () -> Unit = {}) {
        GoogleSignIn.getClient(context, buildOptions())
            .signOut()
            .addOnCompleteListener { onDone() }
    }
}
