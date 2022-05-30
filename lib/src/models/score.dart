class Score {
  String? androidLeaderboardID;
  int? value;

  String? get leaderboardID {
    return androidLeaderboardID;
  }

  Score({this.androidLeaderboardID, this.value});
}
