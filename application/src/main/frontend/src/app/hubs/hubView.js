(function() {
    var m = angular.module('parkandride.hubView', [
        'ui.router',
        'parkandride.hubMap',
        'parkandride.FacilityResource',
        'parkandride.HubResource'
    ]);

    m.config(function config($stateProvider) {
        $stateProvider.state('hub-view', { // dot notation in ui-router indicates nested ui-view
            url: '/hubs/view/:id',
            views: {
                "main": {
                    controller: 'HubViewCtrl as viewCtrl',
                    templateUrl: 'hubs/hubView.tpl.html',
                    resolve: {
                        hub: function($stateParams, HubResource) {
                            return HubResource.getHub($stateParams.id);
                        },
                        summary: function(hub, FacilityResource) {
                            return FacilityResource.summarizeFacilities({ ids: hub.facilityIds });
                        }
                    }
                }
            },
            data: { pageTitle: 'View Hub' }
        });
    });

    m.controller('HubViewCtrl', function($scope, hub, summary, FacilityResource) {
        this.hub = hub;
        this.summary = summary;
    });

    m.directive('hubViewNavi', function() {
        return {
            restrict: 'E',
            templateUrl: 'hubs/hubViewNavi.tpl.html'
        };
    });
})();