"use strict";

module.exports = function () {
    var _ = require('lodash');
    var request = require('request');
    var devApiUrl = browser.baseUrl + '/dev-api',
        facilitiesUrl = devApiUrl + '/facilities',
        hubsUrl = devApiUrl + '/hubs';
    var flow = protractor.promise.controlFlow();

    var api = {};

    function asPromise(options) {
        var defer = protractor.promise.defer();
        request(options, function(error, message) {
            if (error || message.statusCode >= 400) {
                console.error("backend error: ", message.body);
                defer.reject({
                    error : error,
                    message : message
                });
            } else {
                defer.fulfill(message);
            }
        });
        return defer.promise;
    }

    function asPayload(coll) {
        return _.map(coll, function(item) { return item.toPayload(); });
    }

    api.resetFacilities = function(facilities) {
        flow.execute(function() { return asPromise({ method: 'DELETE', url: facilitiesUrl }); });
        if (facilities) {
            flow.execute(function() { return asPromise({ method: 'PUT', url: facilitiesUrl, json: true, body: asPayload(facilities) }); });
        }
    };

    api.resetHubs = function(hubs) {
        flow.execute(function() { return asPromise({ method: 'DELETE', url: hubsUrl }); });
        if (hubs) {
            flow.execute(function(){ return asPromise({ method: 'PUT', url: hubsUrl, json: true, body: asPayload(hubs) }); });
        }
    };

    api.resetAll = function(facilities, hubs) {
        api.resetFacilities(facilities);
        api.resetHubs(hubs);
    };

    return api;
};