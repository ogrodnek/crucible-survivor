var app = angular.module("CrucibleLeader", []);

function LeaderController($scope) {

  $scope.fame = leaderStats.fame;
  $scope.shame = leaderStats.shame;

  $scope.openReviewData = leaderStats.openCountStats;

  $scope.reviewRateData = leaderStats.openCloseStats;
  $scope.updateDate = leaderStats.updateDate;

  var os = $scope.openReviewData;
  console.log("os", os);
  var delta = os[os.length -1][1] - os[os.length -2][1];


  $scope.reviewStats = {
    openReviews: leaderStats.totalOpenReviews,
    openReviewsDelta: delta,
    deltaChange: delta < 0 ? "neg" : "pos"
  };
}