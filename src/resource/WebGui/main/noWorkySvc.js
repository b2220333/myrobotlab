angular.module('mrlapp.main.noWorkySvc', [])
        .service('noWorkySvc', ['$modal', function ($modal) {
                //own service might be overheat,
                //but it is used in more than one place
                //e.g. navbar & in every service UI
                
                this.openNoWorkyModal = function (reason) {
                    //reason is maybe for later
                    //more advanced NoWorky's? reason = service send from?
                    var modalInstance = $modal.open({
                        animation: true,
                        templateUrl: 'nav/noWorky.html',
                        controller: 'noWorkyCtrl',
//                        size: 'sm',
                        resolve: {
                            reason: function () {
                                return reason;
                            }
                        }
                    });
                };
            }]);
