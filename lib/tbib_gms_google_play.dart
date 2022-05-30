import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/services.dart';

import 'src/models/achievement.dart';

export 'src/models/achievement.dart';

class PluginResult {
  PluginResult(Map<dynamic, dynamic> map) {
    success = map["success"];
    message = map["exception"];
  }

  String? message;
  bool success = false;
}

class SignInResult extends PluginResult {
  SignInResult(Map<dynamic, dynamic> map) : super(map) {
    if (success) {
      email = map["data"];
    }
  }

  String? email;
}

/// the result of LoadSnapshot
class LoadSnapshotResult extends PluginResult {
  LoadSnapshotResult(Map<dynamic, dynamic> map) : super(map) {
    if (success) {
      Uint8List datam = map["data"];
      String bytes = utf8.decode(datam);
      Map yourDataMap = json.decode(bytes);
      data = yourDataMap;
    }
  }

  Map? data;
}

/// Main Class, provider useful method
class TBIBGMSGooglePlay {
  /// the channel communicate with platform
  static const MethodChannel _channel = MethodChannel('tbib_gms_google_play');

  /// SignIn with google account, before you do anything, you must sign in
  /// @param scopeSnapShot set to ture if you want play with snapshots
  static Future signIn() async {
    var result = await _channel.invokeMethod('silentSignIn');
    return result;
  }

  static Future<bool> get isSignedIn async =>
      await _channel.invokeMethod("isSignedIn");

  /// To sign the user out of Goole Play Services.
  /// After calling, you can no longer make any actions
  /// on the user's account.a
  static Future<String?> signOut() async {
    return await _channel.invokeMethod("signOut");
  }

  static Future<String?> unlock({required Achievement achievement}) async {
    return await _channel.invokeMethod("unlock", {
      "achievementID": achievement.id,
      "percentComplete": 100,
    });
  }

  /// Get the player id.
  /// On iOS the player id is unique for your game but not other games.
  static Future<String?> getPlayerID() async {
    return await _channel.invokeMethod("getPlayerID");
  }

  /// Get the snapshot data with the given name
  /// @param name the snapshot's name you want get
  static Future<LoadSnapshotResult> loadSnapShot(String name) async {
    try {
      Map<dynamic, dynamic> map =
          await _channel.invokeMethod('loadSnapShot', {"name": name});
      return LoadSnapshotResult(map);
    } catch (e) {
      return LoadSnapshotResult({"success": false, "exception": e.toString()});
    }
  }

  /// Save data to the snapshot with the given name
  /// @param name the snapshot's name
  /// @param data a byte array you want to save
  /// @param description the description of the snapshot, will add to it's metedata
  static Future<PluginResult> saveSnapShot(
      String name, Map dataMap, String description) async {
    try {
      String yourData = json.encode(dataMap);
      List<int> bytesList = utf8.encode(yourData);

      Uint8List data = Uint8List.fromList(bytesList);

      Map<dynamic, dynamic> result = await _channel.invokeMethod('saveSnapShot',
          {"name": name, "data": data, "description": description});
      return PluginResult(result);
    } catch (e) {
      return PluginResult({"success": false, "exception": e.toString()});
    }
  }

  static Future submitScore(String leaderBoardId, int score) async {
    try {
      Map<dynamic, dynamic> result = await _channel
          .invokeMethod('submitScore', {"id": leaderBoardId, "score": score});
      return PluginResult(result);
    } catch (e) {
      return PluginResult({"success": false, "exception": e.toString()});
    }
  }

  static Future<PluginResult> increment(String achivementId) async {
    try {
      Map<dynamic, dynamic> result =
          await _channel.invokeMethod('increment', {"id": achivementId});
      return PluginResult(result);
    } catch (e) {
      return PluginResult({"success": false, "exception": e.toString()});
    }
  }

  static void showAchievements() async {
    return await _channel.invokeMethod("showAchievements");
  }

  /// It will open the leaderboards screen.
  static Future<String?> showLeaderboards({androidLeaderboardID = ""}) async {
    return await _channel.invokeMethod(
        "showLeaderboards", {"leaderboardID": androidLeaderboardID});
  }
}
