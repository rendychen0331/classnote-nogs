package com.rendy.classnote.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.rendy.classnote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object OneDriveAuthManager {

    private const val TAG = "OneDriveAuthManager"
    val SCOPES = listOf(
        "Files.ReadWrite.AppFolder",
        "Files.ReadWrite",
        "Tasks.Read",
        "Calendars.Read",
        "User.Read"
    )

    // EduAssignments.ReadBasic 只適用學校組織帳號，不放進全域 SCOPES 避免 personal 帳號 login 失敗
    val TEAMS_SCOPES = listOf("EduAssignments.ReadBasic", "User.Read")

    @Volatile
    private var msalApp: ISingleAccountPublicClientApplication? = null

    suspend fun getApp(context: Context): ISingleAccountPublicClientApplication? {
        msalApp?.let { return it }
        return suspendCoroutine { cont ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context.applicationContext,
                R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(app: ISingleAccountPublicClientApplication) {
                        msalApp = app
                        cont.resume(app)
                    }
                    override fun onError(e: MsalException) {
                        Log.e(TAG, "MSAL init error", e)
                        cont.resume(null)
                    }
                }
            )
        }
    }

    suspend fun getCurrentAccount(context: Context): IAccount? {
        val app = getApp(context) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                app.getCurrentAccount().currentAccount
            } catch (e: Exception) {
                Log.e(TAG, "get current account error", e)
                null
            }
        }
    }

    suspend fun getAccountEmail(context: Context): String? =
        getCurrentAccount(context)?.username
            ?: AppPreferences(context).msAccountEmail

    suspend fun signIn(activity: Activity): String? {
        val app = getApp(activity) ?: return null
        return suspendCoroutine { cont ->
            val params = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(SCOPES)
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        AppPreferences(activity.applicationContext).msAccountEmail =
                            result.account.username
                        cont.resume(result.accessToken)
                    }
                    override fun onError(e: MsalException) {
                        Log.e(TAG, "sign-in error: ${e.errorCode} ${e.message}", e)
                        cont.resumeWithException(e)
                    }
                    override fun onCancel() { cont.resume(null) }
                })
                .build()
            app.acquireToken(params)
        }
    }

    suspend fun acquireTokenSilent(context: Context): String? =
        acquireTokenSilentWithScopes(context, SCOPES)

    suspend fun acquireTokenSilentForTeams(context: Context): String? =
        acquireTokenSilentWithScopes(context, TEAMS_SCOPES)

    private suspend fun acquireTokenSilentWithScopes(context: Context, scopes: List<String>): String? {
        val app = getApp(context) ?: return null
        val account = getCurrentAccount(context) ?: return null
        return try {
            withContext(Dispatchers.IO) {
                val params = AcquireTokenSilentParameters.Builder()
                    .withScopes(scopes)
                    .fromAuthority(account.authority)
                    .forAccount(account)
                    .build()
                app.acquireTokenSilent(params).accessToken
            }
        } catch (e: Exception) {
            Log.e(TAG, "silent token error (scopes=$scopes)", e)
            null
        }
    }

    suspend fun signOut(context: Context) {
        AppPreferences(context).msAccountEmail = null
        val app = getApp(context) ?: return
        suspendCoroutine<Unit> { cont ->
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() { cont.resume(Unit) }
                override fun onError(e: MsalException) { cont.resume(Unit) }
            })
        }
    }
}
