'use strict';

angular.module('todoApp.controller',[])
    .controller('todoController', ["$scope", "$filter", function($scope, $filter){
    $scope.newTask = "";

    $scope.taskList = [
        {description: "Buy airplain tickets", done: false},
        {description: "Make hotel reservations", done: false},
        {description: "Chill", done: false}
    ];

    $scope.pendingCount = 3;

    $scope.addTodo = function() {
        $scope.taskList.push({description: $scope.newTask, done: false});
        $scope.newTask = "";
    };

    $scope.deleteTodo = function(index) {
        $scope.taskList.splice(index, 1);
    };

    $scope.$watch('taskList', function() {
        $scope.pendingCount = $filter('filter')($scope.taskList, {done: false}).length;
    }, true);

    $scope.clearCompleted = function() {
        $scope.taskList = $filter('filter')($scope.taskList, {done: false});
    };
}]);
