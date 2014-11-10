//
// Copyright 2014 by Cloudsoft Corporation Limited
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

var clocker = angular.module('clocker', ['ui.bootstrap']);

clocker.controller('infrastructures', function ($scope, $http, $interval) {
  $interval(function() {
    $http.get('/v1/applications/tree').success(function(data) {
      $scope.infrastructures = data.filter(function(value) {
        return value.children[0].type == 'brooklyn.entity.container.docker.DockerInfrastructure';
      });
    });
  }, 5000);
});

clocker.controller('hosts', function ($scope, $rootScope, $http) {
  $scope.applicationId = $scope.infrastructure.id;
  var current = $scope.infrastructure.children[0].children[0].children.filter(function(value) {
    return value.type == 'brooklyn.entity.container.docker.DockerHost';
  });
  if (!$rootScope.collapsed) {
    $rootScope.collapsed = { };
  }
  $scope.hosts = { };
  current.forEach(function(value) {
    $http.get('/v1/applications/' + $scope.applicationId + '/entities/' + value.id + '/sensors/current-state').success(function(data) {
      $scope.hosts[value.id] = data;
      $scope.hosts[value.id].id = value.id;
      if (typeof $rootScope.collapsed[value.id] == 'undefined') {
        $rootScope.collapsed[value.id] = true;
      }
      $http.get('/v1/applications/' + $scope.applicationId + '/entities/' + value.id + '/config/current-state').success(function(data) {
        $scope.hosts[value.id].config = data;
      });
    });
  });
});

clocker.controller('sparklines', function ($scope, $rootScope, $http, $interval) {
  $interval(function() {
    $http.get('/v1/applications/' + $scope.infrastructure.id + '/descendants/sensor/machine.loadAverage?typeRegex=brooklyn.entity.container.docker.DockerHost').success(function(data) {
      $scope.infrastructures = data.filter(function(value) {
        return value.children[0].type == 'brooklyn.entity.container.docker.DockerInfrastructure';
      });
    });
  }, 1000);
});

clocker.controller('containers', function ($scope, $http) {
  $http.get('/v1/applications/' + $scope.applicationId + '/entities/' + $scope.host.id + '/children').success(function(data) {
    $scope.containers = { };
    var cluster = data.filter(function(value) {
      return value.type == 'brooklyn.entity.group.DynamicCluster';
    })[0];
    $http.get('/v1/applications/' + $scope.applicationId + '/entities/' + cluster.id + '/children').success(function(data) {
      data.forEach(function(value) {
        $http.get('/v1/applications/' + $scope.applicationId + '/entities/' + value.id + '/sensors/current-state').success(function(data) {
          $scope.containers[value.id] = data;
          $scope.containers[value.id].id = value.id;
        });
      });
    });
  });
});