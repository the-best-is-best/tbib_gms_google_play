package com.tbib.tbib_gms_google_play

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.view.Gravity
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.drive.Drive
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.Games
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.SnapshotsClient.DataOrConflict
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Task
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import java.io.IOException


private const val CHANNEL_NAME = "tbib_gms_google_play"
private const val RC_SIGN_IN = 9000

class TbibGmsGooglePlayPlugin(private var activity: Activity? = null) : FlutterPlugin, MethodCallHandler, ActivityAware, ActivityResultListener {


  //region Variables
  private var googleSignInClient: GoogleSignInClient? = null
  private var achievementClient: AchievementsClient? = null
  private var leaderboardsClient: LeaderboardsClient? = null
  private var activityPluginBinding: ActivityPluginBinding? = null
  private var channel: MethodChannel? = null
  private var pendingOperation: PendingOperation? = null
  //endregion


  private fun silentSignIn(result: Result) {
    val activity = activity ?: return
    val builder = GoogleSignInOptions.Builder(
      GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
    builder.requestScopes(Games.SCOPE_GAMES_SNAPSHOTS, Drive.SCOPE_APPFOLDER)
    googleSignInClient = GoogleSignIn.getClient(activity, builder.build())
    googleSignInClient?.silentSignIn()?.addOnCompleteListener { task ->
      pendingOperation = PendingOperation(Methods.silentSignIn, result)
      if (task.isSuccessful) {
        val googleSignInAccount = task.result ?: return@addOnCompleteListener
        handleSignInResult(googleSignInAccount)
      } else {
        Log.e("Error", "signInError", task.exception)
        Log.i("ExplicitSignIn", "Trying explicit sign in")
        explicitSignIn()
      }
    }
  }

  private fun explicitSignIn() {
    val activity = activity ?: return
    val builder = GoogleSignInOptions.Builder(
      GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
      .requestEmail()
    googleSignInClient = GoogleSignIn.getClient(activity, builder.build())
    activity.startActivityForResult(googleSignInClient?.signInIntent, RC_SIGN_IN)
  }

  private fun handleSignInResult(googleSignInAccount: GoogleSignInAccount) {
    val activity = this.activity ?: return
    achievementClient = Games.getAchievementsClient(activity, googleSignInAccount)
    leaderboardsClient = Games.getLeaderboardsClient(activity, googleSignInAccount)

    // Set the popups view.
    val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(activity) ?: return
    val gamesClient = Games.getGamesClient(activity, lastSignedInAccount)
    gamesClient.setViewForPopups(activity.findViewById(android.R.id.content))
    gamesClient.setGravityForPopups(Gravity.TOP or Gravity.CENTER_HORIZONTAL)

    finishPendingOperationWithSuccess()
  }

  private val isSignedIn: Boolean get() {
    val activity = this.activity ?: return false
    return GoogleSignIn.getLastSignedInAccount(activity) != null
  }
  //endregion

  //region User
  private fun getPlayerID(result: Result) {
    showLoginErrorIfNotLoggedIn(result)
    val activity = activity ?: return
    val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(activity) ?: return
    Games.getPlayersClient(activity, lastSignedInAccount)
      .currentPlayerId.addOnSuccessListener {
        result.success(it)
      }.addOnFailureListener {
        result.error("error", it.localizedMessage, null)
      }
  }
  //endregion

  //region SignOut
  private fun signOut(result: Result) {
    googleSignInClient?.signOut()?.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        result.success("success")
      } else {
        result.error("error", "${task.exception}", null)
      }
    }
  }
  //endregion

  //region Achievements & Leaderboards
  private fun showAchievements(result: Result) {
    showLoginErrorIfNotLoggedIn(result)
    achievementClient?.achievementsIntent?.addOnSuccessListener { intent ->
      activity?.startActivityForResult(intent, 0)
      result.success("success")
    }?.addOnFailureListener {
      result.error("error", "${it.message}", null)
    }
  }

  private fun unlock(achievementID: String, result: Result) {
    showLoginErrorIfNotLoggedIn(result)
    achievementClient?.unlockImmediate(achievementID)?.addOnSuccessListener {
      result.success("success")
    }?.addOnFailureListener {
      result.error("error", it.localizedMessage, null)
    }
  }

  private fun increment(result: Result, call: MethodCall) {
    showLoginErrorIfNotLoggedIn(result)
    val activity = this.activity ?: return
    Games.getAchievementsClient(
      activity,
      GoogleSignIn.getLastSignedInAccount(activity)!!
    )
      .incrementImmediate(call.argument<String>("id")!!, 1)
      .addOnSuccessListener {
        result.success(
          PluginResult().toMap()
        )
      }
      .addOnFailureListener { e: java.lang.Exception -> result.success(PluginResult(e.toString()).toMap()) }
  }

  private fun showLeaderboards(leaderboardID: String, result: Result) {
    showLoginErrorIfNotLoggedIn(result)
    val onSuccessListener: ((Intent) -> Unit) = { intent ->
      activity?.startActivityForResult(intent, 0)
      result.success("success")
    }
    val onFailureListener: ((Exception) -> Unit) = {
      result.error("error", "${it.message}", null)
    }
    if (leaderboardID.isEmpty()) {
      leaderboardsClient?.allLeaderboardsIntent
        ?.addOnSuccessListener(onSuccessListener)
        ?.addOnFailureListener(onFailureListener)
    } else {
      leaderboardsClient
        ?.getLeaderboardIntent(leaderboardID)
        ?.addOnSuccessListener(onSuccessListener)
        ?.addOnFailureListener(onFailureListener)
    }
  }

  private fun submitScore( result: Result, call: MethodCall) {
    showLoginErrorIfNotLoggedIn(result)
    val activity = this.activity ?: return
    val score: Int = call.argument("score")!!
    Games.getLeaderboardsClient(
      activity,
      GoogleSignIn.getLastSignedInAccount(activity)!!
    )
      .submitScoreImmediate(call.argument("id")!!, score.toLong())
      .addOnSuccessListener {
        result.success(
          PluginResult().toMap()
        )
      }
      .addOnFailureListener { e: java.lang.Exception -> result.success(PluginResult(e.toString()).toMap()) }
  }

  private fun showLoginErrorIfNotLoggedIn(result: Result) {
    if (achievementClient == null || leaderboardsClient == null) {
      result.error("error", "Please make sure to call signIn() first", null)
    }
  }
  //endregion

  //region FlutterPlugin
  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    setupChannel(binding.binaryMessenger)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    teardownChannel()
  }

  private fun setupChannel(messenger: BinaryMessenger) {
    channel = MethodChannel(messenger, CHANNEL_NAME)
    channel?.setMethodCallHandler(this)
  }

  private fun teardownChannel() {
    channel?.setMethodCallHandler(null)
    channel = null
  }
  //endregion

  //region ActivityAware

  private fun disposeActivity() {
    activityPluginBinding?.removeActivityResultListener(this)
    activityPluginBinding = null
  }

  override fun onDetachedFromActivity() {
    disposeActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activityPluginBinding = binding
    activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
  }

  //endregion

  //region PendingOperation
  private class PendingOperation constructor(val method: String, val result: Result)

  private fun finishPendingOperationWithSuccess() {
    Log.i(pendingOperation?.method, "success")
    pendingOperation?.result?.success("success")
    pendingOperation = null
  }

  private fun finishPendingOperationWithError(errorMessage: String) {
    Log.i(pendingOperation?.method, "error")
    pendingOperation?.result?.error("error", errorMessage, null)
    pendingOperation = null
  }
  //endregion

  //region ActivityResultListener
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == RC_SIGN_IN) {
      val result = data?.let { Auth.GoogleSignInApi.getSignInResultFromIntent(it) }
      val signInAccount = result?.signInAccount
      if (result?.isSuccess == true && signInAccount != null) {
        handleSignInResult(signInAccount)
      } else {
        var message = result?.status?.statusMessage ?: ""
        if (message.isEmpty()) {
          message = "Something went wrong " + result?.status
        }
        finishPendingOperationWithError(message)
      }
      return true
    }
    return false
  }
  //endregion

  //region MethodCallHandler
  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      Methods.unlock -> {
        unlock(call.argument<String>("achievementID") ?: "", result)
      }
      Methods.saveSnapShot -> {
        saveSnapShot(call , result)
        }
      Methods.loadSnapShot -> {
        loadSnapShot(call , result)
    }
      Methods.increment -> {
        call.argument<String>("achievementID")
        increment(result, call)
      }
      Methods.submitScore -> {

        submitScore( result, call)
      }
      Methods.showLeaderboards -> {
        val leaderboardID = call.argument<String>("leaderboardID") ?: ""
        showLeaderboards(leaderboardID, result)
      }

      Methods.showAchievements -> {
        showAchievements(result)
      }

      Methods.silentSignIn -> {
        silentSignIn(result)
      }
      Methods.isSignedIn -> {
        result.success(isSignedIn)
      }
      Methods.signOut -> {
        signOut(result)
      }
      Methods.getPlayerID -> {
        getPlayerID(result)
      }
      else -> result.notImplemented()
    }
  }

  private fun loadSnapShot(call: MethodCall, result: Result) {
    showLoginErrorIfNotLoggedIn(result)
    val activity = this.activity ?: return
    val snapshotsClient = Games.getSnapshotsClient(
      activity,
      GoogleSignIn.getLastSignedInAccount(activity)!!
    )
    snapshotsClient.open(
      call.argument("name")!!,
      false,
      SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
    )
      .addOnCompleteListener { task: Task<DataOrConflict<Snapshot?>> ->
        if (task.isSuccessful) {
          val snapshot = task.result.data
          try {
            result.success(PluginResult().setData(snapshot!!.snapshotContents.readFully()).toMap())
          } catch (e: IOException) {
            result.success(PluginResult(e.toString()).toMap())
          }
        } else {
          result.success(PluginResult(task.exception.toString()).toMap())
        }
      }
  }

  private fun saveSnapShot( call: MethodCall , result: Result) {
    showLoginErrorIfNotLoggedIn(result)
    val activity = this.activity ?: return
    val snapshotsClient = Games.getSnapshotsClient(
      activity,
      GoogleSignIn.getLastSignedInAccount(activity)!!
    )
    snapshotsClient.open(
      call.argument("name")!!,
      true,
      SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
    )
      .addOnCompleteListener { task: Task<DataOrConflict<Snapshot?>> ->
        if (task.isSuccessful) {
          val snapshot = task.result.data
          snapshot!!.snapshotContents.writeBytes(call.argument("data")!!)
          val metadataChange = SnapshotMetadataChange.Builder()
            .setDescription(call.argument("description")!!)
            .build()
          snapshotsClient.commitAndClose(snapshot, metadataChange)
            .addOnCompleteListener { task1: Task<SnapshotMetadata?> ->
              if (task1.isSuccessful) {
                result.success(PluginResult().toMap())
              } else {
                result.success(PluginResult(task.exception.toString()).toMap())
              }
            }
        } else {

          result.success(PluginResult(task.exception.toString()).toMap())
        }
      }
  }
  //endregion
}

object Methods {
  const val saveSnapShot="saveSnapShot"
  const val loadSnapShot="loadSnapShot"
  const val unlock = "unlock"
  const val increment = "increment"
  const val submitScore = "submitScore"
  const val showLeaderboards = "showLeaderboards"
  const val showAchievements = "showAchievements"
  const val silentSignIn = "silentSignIn"
  const val isSignedIn = "isSignedIn"
  const val getPlayerID = "getPlayerID"
  const val signOut = "signOut"
}