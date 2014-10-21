"use strict";

module.exports = function () {
    var spec = {};
    var that = {};

    spec.capacityTypes = element.all(by.css(".wdCapacityType"));

    spec.getTypeProperty = function(type, property) {
        return $('.wd' + type + property).getText();
    };

    that.getTypes = function() {
        return spec.capacityTypes.filter(function(e) { return e.isDisplayed(); }).getText();
    };

    that.getBuilt = function(type) {
        return spec.getTypeProperty(type, 'built');
    };

    that.getUnavailable = function(type) {
        return spec.getTypeProperty(type, 'unavailable');
    };

    return that;
};