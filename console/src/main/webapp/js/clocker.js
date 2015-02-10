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

var clocker = angular.module('clocker', ['ui.bootstrap', 'nvd3ChartDirectives']);

clocker.controller('init', function(sparkdata) {
  sparkdata.poll();
});

clocker.controller('infrastructures', function($scope, $http, $interval) {
  $interval(function() {
    $http.get('/v1/applications/tree').success(function(data) {
      $scope.infrastructures = data.filter(function(value) {
        return value.children[0].type == 'brooklyn.entity.container.docker.DockerInfrastructure';
      });
    });
  }, 60000);
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

clocker.factory('sparkdata', function($rootScope, $http, $interval) {
  if (typeof $rootScope.sparkdata == 'undefined') $rootScope.sparkdata = { };
  $rootScope.xFunction = function() { return function(d) { return d[0]; } };
  $rootScope.yFunction = function() { return function(d) { return d[1]; } };
  return {
    poll: function() {
      $http.get('/v1/applications/tree').success(function(data) {
        var infrastructures = data.filter(function(value) {
          return value.children[0].type == 'brooklyn.entity.container.docker.DockerInfrastructure';
        });
        infrastructures.forEach(function(infrastrcture) {
          $interval(function() {
            var milliseconds = Math.round(new Date().getTime() / 1000);
            $http.get('/v1/applications/' + infrastrcture.id + '/descendants/sensor/machine.cpu?typeRegex=brooklyn.entity.container.docker.DockerHost').success(function(data) {
              Object.keys(data).forEach(function(value) {
                if (typeof $rootScope.sparkdata[value] == 'undefined') {
                  $rootScope.sparkdata[value] = [ ];
                }
                $rootScope.sparkdata[value].push([milliseconds, data[value] * 100]);
                if ($rootScope.sparkdata[value].length > 500) {
                    $rootScope.sparkdata[value].shift();
                }
              });
            });
          }, 1000);
        });
      });
    }
  };
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
          if (data['docker.container.entity'] != null) {
            $http.get('/v1/applications/' + $scope.applicationId + '/entities/' + value.id + '/sensors/docker.container.entity?raw=true').success(function(entity) {
              $scope.containers[value.id].entity = entity.id;
              $http.get('/v1/applications/fetch?items=' + entity.id).success(function(fetch) {
                var found = fetch.filter(function(item) {
                  return item.id == entity.id;
                });
                $http.get('/v1/applications/' + found[0].applicationId + '/entities/' + found[0].applicationId).success(function(icon) {
                  if (typeof icon.links.iconUrl != 'undefined') {
                    $scope.containers[value.id].icon = icon.links.iconUrl;
                  } else {
                    $scope.containers[value.id].icon = 'img/cog.png';
                  }
                });
              });
            });
          }
        });
      });
    });
  });
});
