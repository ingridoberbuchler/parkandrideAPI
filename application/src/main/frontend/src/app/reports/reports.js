// Copyright © 2015 HSL <https://www.hsl.fi>
// This program is dual-licensed under the EUPL v1.2 and AGPLv3 licenses.

(function() {
    var m = angular.module('parkandride.reports', [
        'ui.router',
        'parkandride.auth',
        'parkandride.layout'
    ]);

    m.config(function($stateProvider) {
      $stateProvider.state('report-list', {
          parent: 'reportstab',
          url: '/reports',
          views: {
              "main": {
                  controller: 'ReportsCtrl as listCtrl',
                  templateUrl: 'reports/reports.tpl.html'
              }
          },
          data: {pageTitle: 'Reports'}
      });
    });

    m.controller('ReportsCtrl', function($scope, $http) {
      var contentType = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
      $scope.generate = function(name) {
        $http({
          url: 'api/v1/reports/' + name,
          method: "GET",
          headers: {
            'Accept': contentType
          },
          responseType: 'arraybuffer'
        }).success(function (response) {
          var blob = new Blob([response], {type: contentType});
          var objectUrl = URL.createObjectURL(blob);
          saveAs(blob, name);
        });
      };
    });

    m.directive('reportsNavi', function() {
      return {
          restrict: 'E',
          templateUrl: 'reports/reportsNavi.tpl.html'
      };
    });

})();