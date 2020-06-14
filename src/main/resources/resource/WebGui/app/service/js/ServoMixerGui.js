angular.module('mrlapp.service.ServoMixerGui', []).controller('ServoMixerGuiCtrl', ['$scope', '$log', 'mrl', function($scope, $log, mrl) {
    $log.info('ServoMixerGuiCtrl')
    var _self = this
    var msg = this.msg
    $scope.servos = []
    $scope.sliders = []

    // GOOD TEMPLATE TO FOLLOW
    this.updateState = function(service) {
        $scope.service = service
    }

    $scope.toggle = function(servo) {
        $scope.sliders[servo].tracking = !$scope.sliders[servo].tracking
    }

    _self.onSliderChange = function(servoName){
        if (!$scope.sliders[servoName].tracking){
            msg.sendTo(servoName, 'moveTo', $scope.sliders[servoName].value)
        }
    }
    
    $scope.setSearchServo = function(text) {
        $scope.searchServo.displayName = text
    }
    
    $scope.SearchServo = {
        // displayName: ""
    }
    
    this.updateState($scope.service)

    this.onMsg = function(inMsg) {
        switch (inMsg.method) {
        case 'onState':
            _self.updateState(inMsg.data[0])
            $scope.$apply()
            break
        case 'onServoEvent':
            var data = inMsg.data[0];
            $scope.sliders[data.name].value = data.pos;
            $scope.$apply()
            break
        case 'onListAllServos':
            // servos sliders are either in "tracking" or "control" state
            // "tracking" they are moving from callback position info published by servos
            // "control" they are sending control messages to the servos
            $scope.servos = inMsg.data[0]
            for (var servo of $scope.servos) {
                // dynamically build sliders
                $scope.sliders[servo.name] = {
                    value: 0,
                    tracking: false,
                    options: {
                        id: servo.name,
                        floor: 0,
                        ceil: 180,
                        onStart: function(id) {
                            console.info('ServoMixer.onStart')
                        },
                        onChange: function(id) {
                            _self.onSliderChange(id)
                        },
                        /*
                        onChange: function() {
                            if (!this.tracking) {
                                // if not tracking then control
                                msg.sendTo(servo, 'moveToX', sliders[servo].value)
                            }
                        },*/
                        onEnd: function(id) {}
                    }
                }
                // dynamically add callback subscriptions
                // these are "intermediate" subscriptions in that they
                // don't send a subscribe down to service .. yet 
                // that must already be in place (and is in the case of Servo.publishServoEvent)
                msg.subscribeTo(_self, servo.name, 'publishServoEvent')

            }
            $scope.$apply()
            break
        default:
            $log.error("ERROR - unhandled method " + $scope.name + " " + inMsg.method)
            break
        }
    }

    $scope.savePose = function(pose) {
        msg.send('savePose', pose);
    }

    msg.subscribe('listAllServos')
    msg.send('listAllServos')
    msg.subscribe(this)
}
])
