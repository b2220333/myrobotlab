/**
* The initial javascript service created to display MyRobotLab services.
* This javascript client should be considered equivalent to a mrl instance - with a single javascript runtime service.
* Its purpose is to display controls and published data from other services mostly from other processes.
* This UI is a reflection of all the services which have been 'registered' with this javascript service.
* The javascript service is effectively named "runtime@webgui-client-1234-5678" - although it would be more advantageous if the 
* remote Java WebGui passed up its {id}, and managed its uniqueness
*/

angular.module('mrlapp.mrl', []).provider('mrl', [function() {
    console.debug('mrl.js - begin')

    // TODO - get 'real' platform info - browser type - node version - etc
    let platform = {
        os: "chrome",
        lang: "javascript",
        bitness: 64,
        mrlVersion: "unknown"
    }

    var _self = this

    // object containing all panels
    let panels = {}

    // dictionary of images to display and their display properties
    let displayImages = {}

    // list of callback functions to display images
    let displayCallbacks = []

    // global zIndex
    let zIndex = 0

    // search function - setting the nav search
    let searchFunction = null

    // FIXME - let the webgui pass up the id unless configured not to
    function generateId() {
        // one id to rule them all !
        return 'webgui-client-' + 1234 + '-' + 5678
    }

    // The name of the gateway I am
    // currently attached to
    // tried to make these private ..
    // too much of a pain :P
    // FIXME - try again... {}
    let gateway = null

    // FIXME - THIS HAS THE WRONG CONCEPT - PREVIOUSLY IT WAS THE MRL JAVA RUNTIME - IT NEEDS TO BE THE JS RUNTIME !!!
    // FIXME - IT WILL BE OVERWRITTEN ! - THAT NEEDS TO BE FIXED
    let runtime = {
        name: "runtime",
        id: generateId()
    }

    this.id = null

    let remotePlatform = null

    // FIXME - must be "multiple" remoteIds... - although there is only 1 connected runtime
    this.remoteId = null

    this.blockingKeyList = {}

    let connected = false
    let connecting = false

    // FIXMME - serviceTypes is 'type' information - and this will be
    // retrieved from the Java server.  It will become the defintion of what
    // possible services we can support - IT SHOULD NOT COME FROM THE JAVA SERVER
    // instead it should be defined by the {service}Gui.js and {service}View.js we have !
    // FIXME - load this with a request NOT from runtime
    // var serviceTypes = []
    let serviceTypes = {}

    let registry = {}
    let ids = {}

    let methodCache = {}
    let transport = 'websocket'
    let socket = null
    let callbacks = []

    let onStatus = []
    let connectedCallbacks = []
    let deferred = null
    let msgInterfaces = {}

    // configuration for atmosphere websockets
    // https://github.com/Atmosphere/atmosphere/wiki/jQuery.atmosphere.js-atmosphere.js-API
    // See the following link for all websocket configuration
    // https://raw.githubusercontent.com/Atmosphere/atmosphere-javascript/master/modules/javascript/src/main/webapp/javascript/atmosphere.js
    this.request = {
        url: document.location.origin.toString() + '/api/messages',
        transport: 'websocket',
        maxRequest: 100,
        enableProtocol: true,
        fallbackTransport: 'long-polling',
        // trackMessageLength: true,
        // maxTextMessageSize: 10000000,
        // maxBinaryMessageSize: 10000000,
        logLevel: 'info'
    }

    // connectivity related end
    var msgCount = 0

    // FIXME - clean up maps and target vs source runtime
    // msg map of js runtime 
    var jsRuntimeMethodMap = {}
    // map of 'full' service names to callbacks
    var nameCallbackMap = {}

    // map of method names to callbacks
    var methodCallbackMap = {}
    // specific name & method callback
    // will be used by framework
    var nameMethodCallbackMap = {}

    var jsRuntimeMethodCallbackMap = {}

    function capitalize(string) {
        return string.charAt(0).toUpperCase() + string.slice(1)
    }

    // FIXME CHECK FOR DUPLICATES
    this.subscribeToService = function(callback, inName) {
        let serviceName = _self.getFullName(inName)
        if (!(serviceName in nameCallbackMap)) {
            nameCallbackMap[serviceName] = []
        }
        nameCallbackMap[serviceName].push(callback)
    }

    // NEW !!! - subscribe to a specific instance.method callback
    // will be used by the framework
    this.subscribeToServiceMethod = function(callback, serviceName, methodName) {
        var key = _self.getFullName(serviceName) + "." + getCallBackName(methodName)
        if (!(key in nameMethodCallbackMap)) {
            nameMethodCallbackMap[key] = []
        }
        nameMethodCallbackMap[key].push(callback)
    }

    this.subscribeToMethod = function(callback, methodName) {
        if (!(methodName in methodCallbackMap)) {
            methodCallbackMap[methodName] = []
        }
        methodCallbackMap[methodName].push(callback)
    }

    /**
     * FIXME - use a callback method like addServicePanel
     * registered is called when the subscribed remote runtime.registered is published and this
     * callback is invoked
     */
    this.onReleased = function(msg) {
        var service = msg.data[0]

        // FIXME - unregister from all callbacks
        delete registry[_self.getFullName(service)]
    }

    // FIXME - the Runtime.cli uses this
    this.sendBlockingMessage = function(msg) {

        let promise = new Promise(function(resolve, reject) {

            // timer to check at interval
            (function theLoop(msg, i) {
                setTimeout(function() {
                    console.log("blocking timer looking for " + msg.msgId + " in blockingKeyList " + JSON.stringify(msg))
                    // if > 0 ...
                    if (i--) {
                        if (msg.msgId in _self.blockingKeyList) {
                            console.log("found blocking message return")
                            resolve(_self.blockingKeyList[msg.msgId])
                            delete _self.blockingKeyList[msg.msgId]
                        }
                        if (i == 0) {
                            // else error it !
                            console.log("timer exceeded")
                            // TODO - don't throw error - and log error in .then().catch()
                            reject(new Error("timeout for blocking msg " + JSON.stringify(msg)))
                        }
                        // If i > 0, keep going
                        theLoop(msg, i)
                        // Call the loop again
                    }
                }, 1000)
            }
            )(msg, 20)
            // you can add arbitrary amount of parameters
        }
        )

        _self.sendMessage(msg)
        return promise
    }

    this.sendMessage = function(msg) {
        var cleanJsonData = []
        if (msg.data != null && msg.data.length > 0) {
            // reverse encoding - pop off undefined
            // to shrink paramter length
            // js implementation -
            var pos = msg.data.length - 1
            for (i = pos; i > -1; --i) {
                // WTF? - why do this ? - it's a bug for overloaded method
                // ProgramAB.getResponse(null, 'hello') --> resolves to --> ProgramAB.getResponse(null)
                if (typeof msg.data[i] == 'undefined') {// msg.data.pop() RECENTLY CHANGED 2016-01-21 - popping changes signature !!!! - changing to NOOP
                } else {
                    msg.data[i] = JSON.stringify(msg.data[i])
                }
            }
        }

        var json = JSON.stringify(msg)
        _self.sendRaw(json)
    }

    this.sendRaw = function(msg) {
        socket.push(msg)
    }

    this.onHelloResponse = function(response) {
        console.log('onHelloResponse:')
        console.log(response)
    }

    /**
     * This is what a remote service will send when it wants a service to be registered here.
     * onRegistered msgs get sent on initial connection, and if Runtime.registered is subscribed too,
     * they will get sent on any new service registered on the remote system
     */
    this.onRegistered = function(msg) {

        let registration = msg.data[0]
        let fullname = registration.name + '@' + registration.id
        console.log("--> onRegistered " + fullname)

        let simpleTypeName = _self.getSimpleName(registration.typeKey)

        // FIXME - what the hell its expecting a img - is this needed ????
        registration['img'] = simpleTypeName + '.png'
        // model.alt = serviceType.description - do not have that info at the moment                                                       
        serviceTypes[simpleTypeName] = registration.type

        // initial de-serialization of state
        let service = JSON.parse(registration.state)
        registry[fullname] = service

        // now add a panel - with the function it registered
        // _self.addServicePanel(service)
        _self.addService(service)

        //        deferred.resolve('onRegistered - we have a new service !')
    }

    /**
     * The first message recieved from remote webgui process.  After a connection is negotiated, a "hello" msg is
     * sent by both processes. The processes then respond to each other and begin the dialog of asking for details of
     * services and continuing additional setup.  The hello has a summarized list of services which only include name,
     * and type. It will wait until the foreign process sends a onRegistered
     * with details of state info
     */
    this.getHelloResponse = function(request) {
        console.log('--> got getHelloResponse: and set jsRuntimeMethodCallbackMap')
        let hello = JSON.parse(request.data[1])

        remotePlatform = hello.platform
        // FIXME - remove this - there aren't 1 remoteId there are many !
        _self.remoteId = hello.id

        // once we have our mrl instance's id - we are
        // ready to ask more questions

        // FIXME - git list of current services (name and types)
        // FIXME - iterate through list request getService on each -or publishState
        platform.mrlVersion = hello.platform.mrlVersion
        // FIXME - Wrong !  - multiple platforms !

        // suscribe to future registration requests

        let fullname = 'runtime@' + hello.id
        jsRuntimeMethodCallbackMap[fullname + '.onRegistered'] = _self.onRegistered
        jsRuntimeMethodCallbackMap[fullname + '.onReleased'] = _self.onReleased

        _self.subscribe(fullname, 'registered')
        _self.subscribe(fullname, 'released')

        // FIXME - remove the over-complicated promise
        console.log('--> got getHelloResponse: end')
    }

    /**
     * onMessage gets all messaging from the remote websocket server
     * all asynchronous callbacks will be routed here.  All
     * messages will be in a Message strucutre except for the
     * Atmosphere heartbeat
     */
    this.onMessage = function(response) {
        ++msgCount
        var body = response.responseBody
        if (body == 'X') {
            console.log("heartbeat:", body)
        } else {
            var msg
            try {
                msg = jQuery.parseJSON(body)

                if (msg == null) {
                    console.log('msg null')
                    return
                }

                // GREAT FOR DEBUGGING INCOMING MSGS
                // console.warn(msg.sender + '---> ' + msg.name + '.' + msg.method)

                // handle blocking 'R'eturn msgs here - FIXME - timer to clean old errored msg ?
                // the blocking call removes any msg resolved
                if (msg.msgType == 'R') {
                    _self.blockingKeyList[msg.msgId] = msg
                }

                // TODO - msg "to" the jsRuntime .. TODO - all all methods of this class
                // HIDDEN single javascript service -> runtime@webgui-client-1234-5678
                // handles all delegation of incoming msgs and initial registrations
                // e.g. : runtime@remote-robot.onRegistered
                // FIXME - this is wrong - its handling callbacks from the connected Runtime - there can be
                // multiple connected runtimes.  This "should" handle all msg.name == js runtime (not sender)
                key = msg.sender + '.' + msg.method
                if (jsRuntimeMethodCallbackMap.hasOwnProperty(key)) {
                    let cbs = jsRuntimeMethodCallbackMap[key]
                    cbs(msg)
                }

                // THE CENTER OF ALL CALLBACKS
                // process name callbacks - most common
                // console.log('nameCallbackMap')
                let senderFullName = _self.getFullName(msg.sender)
                if (nameCallbackMap.hasOwnProperty(senderFullName) && msg.method != 'onMethodMap') {
                    let cbs = nameCallbackMap[senderFullName]
                    for (var i = 0; i < cbs.length; i++) {
                        cbs[i](msg)
                    }
                }
                // serviceName.methodName callback
                // framework subscribes to (name).onMethodMap to build all
                // underlying structured methods based on Java reflected descriptions
                // console.log('nameMethodCallbackMap')
                key = _self.getFullName(msg.sender) + '.' + msg.method
                if (nameMethodCallbackMap.hasOwnProperty(key)) {
                    let cbs = nameMethodCallbackMap[key]
                    for (var i = 0; i < cbs.length; i++) {
                        cbs[i](msg)
                    }
                }
                // TODO - type based callbacks - rare, except for Runtime
                // process method callbacks - rare - possible collisions
                // 'onHandleError' might be worthwhile - mrl managed error
                // console.log('methodCallbackMap');
                if (methodCallbackMap.hasOwnProperty(msg.method)) {
                    let cbs = methodCallbackMap[msg.method]
                    for (var i = 0; i < cbs.length; i++) {
                        cbs[i](msg)
                    }
                }
            } catch (e) {
                console.log('Error onMessage: ', e, body)
                return
            }
        }
    }

    this.onTransportFailure = function(errorMsg, request) {
        console.error('mrl.onTransportFailure')
        if (window.EventSource) {
            request.fallbackTransport = "sse"
        } else {
            request.fallbackTransport = 'long-polling'
        }
        transport = request.fallbackTransport
        console.log('Error: falling back to ' + transport + ' ' + errorMsg)
    }

    this.onStatus = function(status) {
        console.log(status)
    }

    this.onClose = function(response) {
        connected = false
        console.error('mrl.onClose')
        // I doubt the following is correct or needed
        // just because the connection to the WebGui service fails
        // does not mean callbacks should be removed ...

        if (response.state == "unsubscribe") {
            console.log('Info: ' + transport + ' closed.')
        }

        angular.forEach(connectedCallbacks, function(value, key) {
            value(connected)
        })
    }

    this.getShortName = function(name) {
        if (name.includes('@')) {
            return name.substring(0, name.indexOf("@"))
        } else {
            return name
        }
    }

    this.getStyle = function(bool) {
        if (bool) {
            return ['btn', 'btn-default', 'active']
        } else {
            return ['btn', 'btn-default']
        }
    }

    this.getFullName = function(service) {

        if ((typeof service) == "string") {
            if (service.includes('@')) {
                return service
            } else {
                // is string - and is short name - check registry first
                if (_self.remoteId != null) {
                    console.error('name \"' + service + '\" string supplied name did not have remoteId - this will be a problem !')
                    return service + '@' + _self.remoteId
                } else {
                    return service
                }
            }
        } else {

            if (service.name.includes('@')) {
                return service.name
            } else {
                return service.name + '@' + service.id
            }
        }
    }

    // --------- ws end ---------------------
    // TODO createMessage
    this.createMessage = function(inName, inMethod, inParams) {
        // TODO: consider a different way to pass inParams for a no arg method.
        // rather than an array with a single null element.
        let rSuffix = (_self.remoteId == null || inName.includes('@')) ? "" : "@" + _self.remoteId

        var msg = {
            msgId: new Date().getTime(),
            name: _self.getFullName(inName),
            method: inMethod,
            sender: "runtime@" + _self.id,
            sendingMethod: null
        }

        if (inParams == null || (inParams.length == 1 && inParams[0] === null)) {

            return msg
        } else {
            msg["data"] = inParams
            return msg
        }
    }

    this.getServicesFromInterface = function(interface) {
        var ret = []
        for (var name in registry) {
            var service = registry[name]
            // see if a service has the same input interface
            if (!angular.isUndefined(service.interfaceSet) && !angular.isUndefined(service.interfaceSet[interface])) {
                ret.push(registry[name])
            }
        }
        return ret
    }

    this.getService = function(name) {

        if (registry[_self.getFullName(name)] == null) {
            return null
        }
        return registry[_self.getFullName(name)]
    }

    this.addService = function(service) {
        registry[_self.getFullName(service)] = service
    }

    this.removeService = function(name) {
        delete registry[_self.getFullName(name)]
    }

    function getCallBackName(topicMethod) {
        // replacements
        if (topicMethod.startsWith("publish")) {
            return 'on' + capitalize(topicMethod.substring("publish".length))
        } else if (topicMethod.startsWith("get")) {
            return 'on' + capitalize(topicMethod.substring("get".length))
        }
        // no replacement - just pefix and capitalize
        // FIXME - subscribe to onMethod --- gets ---> onOnMethod :P
        return 'on' + capitalize(topicMethod)
    }

    this.sendTo = function(name, method, data) {
        var args = Array.prototype.slice.call(arguments, 2)
        var msg = _self.createMessage(name, method, args)
        msg.sendingMethod = 'sendTo'
        _self.sendMessage(msg)
    }

    this.sendToBlocking = function(name, method, data) {
        var args = Array.prototype.slice.call(arguments, 2)
        var msg = _self.createMessage(name, method, args)
        msg.msgType = "B"
        if (msg.sendingMethod == null) {
            msg.sendingMethod = 'sendToBlocking'
        }
        // console.log('SendTo:', msg)
        _self.sendBlockingMessage(msg)
    }

    /*
        FIXME - these are probably add and remove listeners for the js runtime service - NOT NEEDED
        - add the callbacks - remove these methods

        The "real" subscribe - this creates a subscription
        from the Java topicName service, such that every time the
        topicMethod is invoked a message comes back to the gateway(webgui),
        from there it is relayed to the Angular app - and will be sent
        to all the callbacks which have been registered to it
        topicName.topicMethod ---> webgui gateway --> angular callback
    */
    this.subscribe = function(name, method) {
        _self.sendTo(name, "addListener", method, 'runtime@' + _self.id)
    }

    this.unsubscribe = function(name, method) {
        _self.sendTo(name, "removeListener", method, 'runtime@' + _self.id)
    }

    this.invoke = function(functionName, context) {
        var args = [].slice.call(arguments).splice(2)
        var namespaces = functionName.split(".")
        var func = namespaces.pop()
        for (var i = 0; i < namespaces.length; i++) {
            context = context[namespaces[i]]
        }
        return context[func].apply(this, args)
    }
    // FIXME - lazy loading Angular modules -
    // http://stackoverflow.com/questions/18591966/inject-module-dynamically-only-if-required
    // the following method should dynamically load the
    // Angular module
    // if not loaded &
    // instanciate a new environments with the (service)
    // data
    this.register = function(service) {
        let fullname = service.name + '@' + service.id
        registry[fullname] = {}
        registry[fullname] = service
        // broadcast callback
        for (var i = 0; i < callbacks.length; i++) {
            callbacks[i](service)
        }
    }
    this.registerForServices = function(callback) {
        this.serviceListeners.push(callback)
    }
    this.onOpen = function(response) {
        console.debug('mrl.onOpen begin')

        // FIXME - does this need to be done later when ids are setup ?
        connected = true
        connecting = false
        //        deferred.resolve('connected !')

        // connected = true
        // this.connected = true mrl.isConnected means data
        // was asked and recieved from the backend
        console.log('mrl.onOpen: ' + transport + ' connection opened')

        let hello = {
            id: _self.id,
            uuid: _self.uuid,
            platform: platform
        }

        console.log('mrl.onOpen: connectedCallbacks ' + connectedCallbacks.length)

        angular.forEach(connectedCallbacks, function(value, key) {
            value(connected)
        })

        // blocking in the sense it will take the return data switch sender & destination - place an 'R'
        // and effectively return to sender without a subscription
        // _self.sendToBlocking('runtime', "getHelloResponse", "fill-uuid", hello)
        // FIXME - this is not full address - but its being sent to a remote runtime :()

        // var msg = _self.createMessage('runtime', "getHelloResponse", "fill-uuid", hello)
        // msg.msgType = 'B' // no timeout - simple 'B'locking expects a resturn msg
        // _self.sendMessage(msg)
        console.debug('sending getHelloResponse to host runtime with hello ' + JSON.stringify(hello))

        _self.sendTo('runtime', "getHelloResponse", "fill-uuid", hello)

        console.debug('mrl.onOpen begin')

    }

    this.getSimpleName = function(fullname) {
        return (fullname.substring(fullname.lastIndexOf(".") + 1))
    }

    this.subscribeConnected = function(callback) {
        connectedCallbacks.push(callback)
    }

    // injectables go here
    // the special $get method called when
    // a service gets instantiated for the 1st time?
    // it also represents config's view of the provider
    // when we inject our provider into a function by way of the provider name ("mrl"), Angular will call $get to
    // retrieve the object to inject
    this.$get = function($q, $log, $http, $templateCache, $ocLazyLoad) {
        // panelSvc begin -----------------------------------

        // var run = function() {
        console.debug('mrl.js $get')
        var lastPosY = -40

        $http.get('service/tab-header.html').then(function(response) {
            $templateCache.put('service/tab-header.html', response.data)
        })

        $http.get('service/tab-footer.html').then(function(response) {
            $templateCache.put('service/tab-footer.html', response.data)
        })

        //START_update-notification
        //notify all list-displays (e.g. main or min) that a panel was added or removed
        //TODO: think of better way
        //-> not top priority, works quite well
        var updateSubscribtions = []
        _self.subscribeToUpdates = function(callback) {
            updateSubscribtions.push(callback)
        }

        // lovely function - https://stackoverflow.com/questions/19098797/fastest-way-to-flatten-un-flatten-nested-json-objects
        _self.flatten = function(data) {
            var result = {};
            function recurse(cur, prop) {
                if (Object(cur) !== cur) {
                    result[prop] = cur;
                } else if (Array.isArray(cur)) {
                    for (var i = 0, l = cur.length; i < l; i++)
                        recurse(cur[i], prop + "[" + i + "]");
                    if (l == 0)
                        result[prop] = [];
                } else {
                    var isEmpty = true;
                    for (var p in cur) {
                        isEmpty = false;
                        recurse(cur[p], prop ? prop + "." + p : p);
                    }
                    if (isEmpty && prop)
                        result[prop] = {};
                }
            }
            recurse(data, "");
            return result;
        }

        _self.unflatten = function(data) {
            "use strict";
            if (Object(data) !== data || Array.isArray(data))
                return data;
            var regex = /\.?([^.\[\]]+)|\[(\d+)\]/g
              , resultholder = {};
            for (var p in data) {
                var cur = resultholder, prop = "", m;
                while (m = regex.exec(p)) {
                    cur = cur[prop] || (cur[prop] = (m[2] ? [] : {}));
                    prop = m[2] || m[1];
                }
                cur[prop] = data[p];
            }
            return resultholder[""] || resultholder;
        }

        _self.unsubscribeFromUpdates = function(callback) {
            var index = updateSubscribtions.indexOf(callback)
            if (index != -1) {
                updateSubscribtions.splice(index, 1)
            }
        }

        var notifyAllOfUpdate = function() {
            var panellist = _self.getPanelList()
            angular.forEach(updateSubscribtions, function(value, key) {
                value(panellist)
            })
        }

        //END_update-notification
        _self.getPanels = function() {
            //return panels as an object
            return panels
        }

        // TODO - implement
        _self.savePanels = function() {
            console.debug("here")
        }
        /**
         * panelSvc (PanelData) --to--> MRL
         * saves panel data from panelSvc to MRL
         */
        _self.savePanel = function(name) {
            mrl.sendTo(_self.gateway.name, "savePanel", _self.getPanelData(name))
        }

        _self.setViewType = function(viewType) {
            _self.viewType = viewType
        }

        _self.getViewType = function() {
            return _self.viewType
        }

        /**
         * return a flattened sorted array of properties for input service
         * TODO - add exclude replacement and info parameters
         */
        _self.getProperties = function(service) {
            let flat = _self.flatten(service)
            // console.table(flat) -  very cool logging, but to intensive

            properties = []

            let exclude = ['serviceType', 'id', 'simpleName', 'interfaceSet', 'serviceClass', 'statusBroadcastLimitMs', 'isRunning', 'name', 'creationOrder', 'serviceType']

            // FIXME - extract from javadoc !
            let info = {
                autoDisable: "servo will de-energize if no activity occurs in {idleTimeout} ms - saving the servo from unnecessary wear or damage",
                idleTimeout: "number of milliseconds the servo will de-energize if no activity has occurred",
                isSweeping: "servo is in sweep mode - which will make the servo swing back and forth at current speed between min and max values",
                lastActivityTimeTs: "timestamp of last move servo did"
            }

            // Push each JSON Object entry in array by [key, value]
            for (let i in flat) {

                let o = flat[i]

                let excluded = false

                for (let j = 0; j < exclude.length; j++) {
                    if (i.startsWith(exclude[j])) {
                        excluded = true
                        break;
                    }
                }

                if (excluded) {
                    continue
                }

                let inf = (info[i] == null) ? '' : info[i]

                properties.push([i, flat[i], inf])
            }

            // Run native sort function and returns sorted array.
            return properties.sort()
        }

        _self.getPanelList = function() {
            return Object.keys(panels).map(function(key) {
                return panels[key]
            })
        }

        _self.display = function(imageSrc, name) {
            if (!name) {
                name = 'image-' + Object.keys(displayImages).length
            }
            displayImages[name] = createPanel(name, name, 15, lastPosY, 800, 0, zIndex, imageSrc)
            for (i = 0; i < displayCallbacks.length; ++i) {
                displayCallbacks[i](displayImages[name])
            }
        }

        let setDisplayCallback = function(callback) {
            displayCallbacks.push(callback)
        }

        let getDisplayImages = function() {
            return displayImages
        }

        let addPanel = function(service) {
            var fullname = _self.getFullName(service)

            if (panels.hasOwnProperty(fullname)) {
                $log.warn(fullname + ' already has panel')
                return panels[fullname]
            }
            lastPosY += 40
            zIndex++
            //construct panel & add it to dictionary
            panels[fullname] = createPanel(fullname, service.serviceClass, 15, lastPosY, 800, 0, zIndex)
            return panels[fullname]
        }

        let createPanel = function(fullname, type, x, y, width, height, zIndex, data) {

            panel = {
                simpleName: _self.getSimpleName(type),
                name: fullname,
                displayName: _self.getShortName(fullname),

                //the state the loading of the template is in (loading, loaded, notfound) - probably can be removed
                templatestatus: null,
                // service.templatestatus,
                // ???
                list: 'main',
                size: 'free',

                data: data,

                posX: x,
                posY: y,
                width: width,
                height: height,
                zIndex: zIndex,
                hide: false,

                // FIXME  - remove this use mrl panel methods
                svc: _self,
                hide: function() {
                    hide = true
                }
            }

            return panel
        }

        _self.addService = function(service) {
            console.debug('mrl.addService ' + service.name)

            var name = _self.getFullName(service)
            var type = service.simpleName
            //first load & parse the controller,    //js
            //then load and save the template       //html
            $log.debug('lazy-loading:', service.name, type)
            $ocLazyLoad.load('service/js/' + type + 'Gui.js').then(function() {
                $log.debug('lazy-loading successful:', service.name, type)
                $http.get('service/views/' + type + 'Gui.html').then(function(response) {
                    $templateCache.put(type + 'Gui.html', response.data)
                    var newPanel = addPanel(service)
                    newPanel.templatestatus = 'loaded'
                    notifyAllOfUpdate()
                }, function(response) {
                    addPanel(name).templatestatus = 'notfound'
                    notifyAllOfUpdate()
                })
            }, function(e) {
                // http template failure
                type = "No"
                // becomes NoGui
                $log.warn('lazy-loading wasnt successful:', type)
                addPanel(name).templatestatus = 'notfound'
                notifyAllOfUpdate()
            })
        }

        // TODO - releasePanel
        _self.releasePanel = function(inName) {
            //remove a service and it's panels
            let name = mrl.getFullName(inName)
            console.debug('removing service', name)
            //remove panels
            if (name in panels) {
                delete panels[name]
            }

            //update !
            notifyAllOfUpdate()
        }

        // TODO remove it then - if it will be abused ... 
        _self.controllerscope = function(name, scope) {
            //puts a reference to the scope of a service
            //in the service & it's panels
            //WARNING: DO NOT ABUSE THIS !!!
            //->it's needed to bring controller & template together
            //->and should otherwise only be used in VERY SPECIAL cases !!!
            console.debug('registering controllers scope', name, scope)
            if ('scope'in panels[name]) {
                $log.warn('replacing an existing scope for ' + name)
            }

            // hanging 'all service' related properties on the instance scope
            scope.showProperties = false
            scope.showMethods = false
            scope.properties = []
            scope.statusControlMode = 'status'
            scope.viewType = _self.viewType

            // status or control - mode of properties
            scope.changeMode = function() {
                scope.statusControlMode = (scope.statusControlMode == 'status') ? 'control' : 'status'
                /*
                if (scope.statusControlMode == 'status') {
                    $scope.pos.options.disabled = true;
                    $scope.limits.options.disabled = true;
                } else {
                    $scope.pos.options.disabled = false;
                    $scope.limits.options.disabled = false;
                }
                */

            }

            panels[name].scope = scope
        }

        _self.putPanelZIndexOnTop = function(name) {
            //panel requests to be put on top of the other panels
            console.debug('putPanelZIndexOnTop', name)
            zIndex++
            panels[name].zIndex = zIndex
            panels[name].notifyZIndexChanged()
        }

        _self.movePanelToList = function(name, panelname, list) {
            //move panel to specified list
            console.debug('movePanelToList', name, panelname, list)
            panels[name].list = list
            notifyAllOfUpdate()
        }

        /**
         * MRL panelData ----to----> UI
         * setPanel takes panelData from a foriegn source
         * and notifies the scope so the gui panels arrange and positioned properly
         */
        _self.setPanel = function(newPanel) {

            if (!(newPanel.name in panels)) {
                console.debug('service ' + newPanel.name + ' currently does not exist yet')
                return
            }

            panels[newPanel.name].name = newPanel.name
            panels[newPanel.name].displayName = _self.getShortName(newPanel.name)
            if (newPanel.simpleName) {
                panels[newPanel.name].simpleName = newPanel.simpleName
            }
            panels[newPanel.name].posY = newPanel.posY
            panels[newPanel.name].posX = newPanel.posX
            panels[newPanel.name].width = newPanel.width
            panels[newPanel.name].height = newPanel.height
            zIndex = (newPanel.zIndex > zIndex) ? (newPanel.zIndex + 1) : zIndex
            panels[newPanel.name].zIndex = newPanel.zIndex
            panels[newPanel.name].hide = newPanel.hide
            // data has been updated - now proccess the changes
            //            panels[newPanel.name].notifyPositionChanged()  // tabs breaks panels
            //            panels[newPanel.name].notifyZIndexChanged() // tabs breaks panels
            //            panels[newPanel.name].notifySizeChanged() // tabs breaks panels
            notifyAllOfUpdate()
            // <-- WTF is this?
        }
        /**
         * getPanelData - input is a panels name
         * output is a panelData object which which will serialize into a 
         * WebGui's PanelData object - we have to create a data object from the
         * angular "panel" since the angular panels cannot be serialized due to
         * circular references and other contraints
         */
        _self.getPanelData = function(panelName) {
            return {
                "name": panels[panelName].name,
                "simpleName": panels[panelName].simpleName,
                "posX": panels[panelName].posX,
                "posY": panels[panelName].posY,
                "zIndex": panels[panelName].zIndex,
                "width": panels[panelName].width,
                "height": panels[panelName].height,
                "hide": panels[panelName].hide
            }
        }

        function show(panelName) {
            panels[panelName].hide = false
        }

        function hide(name) {
            panels[name].hide = true
            _self.savePanel(name)
        }

        function showAll(show) {
            //hide or show all panels
            console.debug('showAll', show)
            angular.forEach(panels, function(value, key) {
                value.hide = !show
                _self.savePanel(key)
            })
        }

        this.connect = function(url, proxy) {
            if (connected) {
                console.debug("aleady connected")
                return this
            }
            // TODO - use proxy for connectionless testing
            if (url != undefined && url != null) {
                this.url = url
            }

            connecting = true
            socket = atmosphere.subscribe(this.request)

        }

        this.onError = function(response) {
            $log.error('onError, can not connect')
        }

        _self.setSearchFunction = function(ref) {
            searchFunction = ref
        }

        /**
         * search panels using the nav search input
         */
        _self.search = function(text) {
            if (searchFunction) {
                searchFunction(text)
            }
        }

        // the Angular service interface object
        var service = {
            getGateway: function() {
                return _self.gateway
            },
            /** 
             * creating a message interface unique for each service, and dynamically building out methods
             * based on information from getMessageMap
             */
            createMsgInterface: function(name) {
                //TODO - clean up here !!!
                //left kind of a mess here (e.g. temp and mod below), MaVo
                var deferred = $q.defer()
                if (!msgInterfaces.hasOwnProperty(name)) {
                    //console.log(name + ' getMsgInterface ')
                    msgInterfaces[name] = {
                        "name": name,
                        "temp": {},
                        send: function(method, data) {
                            var args = Array.prototype.slice.call(arguments, 1)
                            var msg = _self.createMessage(name, method, args)
                            msg.sendingMethod = 'sendTo'
                            // FIXME - not very useful
                            _self.sendMessage(msg)
                        },
                        sendBlocking: function(method, data) {
                            var args = Array.prototype.slice.call(arguments, 1)
                            var msg = _self.createMessage(name, method, args)
                            msg.sendingMethod = 'sendTo'
                            msg.msgType = 'B'
                            // FIXME - not very useful
                            _self.sendMessage(msg)
                        },
                        sendTo: function(toName, method, data) {
                            var args = Array.prototype.slice.call(arguments, 2)
                            var msg = _self.createMessage(toName, method, args)
                            msg.sendingMethod = 'sendTo'
                            _self.sendMessage(msg)
                        },
                        /**
                         *   sendArgs will be called by the dynamically generated code interface
                         */
                        sendArgs: function(method, obj) {
                            data = []
                            for (var key in obj) {
                                if (obj.hasOwnProperty(key)) {
                                    data.push(obj[key])
                                }
                            }
                            var msg = _self.createMessage(name, method, data)
                            msg.sendingMethod = 'sendTo'
                            // FIXME - not very useful
                            _self.sendMessage(msg)
                        },
                        // framework routed callbacks come here
                        onMsg: function(msg) {
                            // webgui.onMethodMap gets processed here
                            // console.log("framework callback " + msg.name + "." + msg.method)
                            switch (msg.method) {
                                // FIXME - bury it ?
                            case 'onState':
                                _self.updateState(msg.data[0])
                                //                                $apply() scope is context related !!!
                                break
                            case 'onMethodMap':
                                // console.log('onMethodMap Yay !!')
                                // method maps are dynamically created binding functions
                                // created to allow direct access from html views to a msg.{method}
                                // bound to a service
                                try {

                                    var methodMap = msg.data[0]
                                    for (var method in methodMap) {
                                        if (methodMap.hasOwnProperty(method)) {
                                            var m = methodMap[method]
                                            var dynaFn = "(function ("
                                            var argList = ""
                                            for (i = 0; i < m.parameterTypeNames.length; ++i) {
                                                if (i != 0) {
                                                    argList += ','
                                                }
                                                argList += "arg" + i
                                            }
                                            dynaFn += argList + "){"
                                            dynaFn += "this._interface.sendArgs('" + m.name + "', arguments);"
                                            dynaFn += "})"
                                            //console.log("msg." + m.name + " = " + dynaFn)
                                            msgInterfaces[msg.sender].temp.msg[m.name] = eval(dynaFn)
                                        }
                                    }
                                    msgInterfaces[msg.sender].temp.methodMap = methodMap
                                } catch (e) {
                                    $log.error("onMethodMap blew up - " + e)
                                }
                                deferred.resolve(msgInterfaces[msg.sender])
                                break
                            default:
                                console.log("ERROR - unhandled method " + msg.method)
                                break
                            }
                            // end switch
                        },
                        subscribeToMethod: function(callback, methodName) {
                            _self.subscribeToMethod(callback, methodName)
                        },
                        subscribeTo: function(controller, serviceName, methodName) {
                            _self.subscribeToServiceMethod(controller.onMsg, serviceName, methodName)
                        },
                        unsubscribe: function(data) {
                            if ((typeof arguments[0]) == "string") {
                                // only handle string argument
                                if (arguments.length != 1) {
                                    console.log("ERROR - unsubscribe expecting 1 arg got " + arguments.length)
                                    return
                                }
                                _self.sendTo(name, "removeListener", arguments[0], 'runtime@' + _self.id)
                            } else {
                                console.error("ERROR - unsubscribe non string arg")
                            }
                        },

                        subscribe: function(data) {
                            if ((typeof arguments[0]) == "string") {
                                // regular subscribe when used - e.g. msg.subscribe('publishData')
                                if (arguments.length != 1) {
                                    console.log("subscribe(string) expecting single argument!")
                                    return
                                }
                                _self.sendTo(name, "addListener", arguments[0], 'runtime@' + _self.id)
                            } else {
                                // controller registering for framework subscriptions
                                //                                console.log("here")
                                // expected 'framework' level subscriptions - we should at a minimum
                                // be interested in state and status changes of the services
                                _self.sendTo(name, "addListener", "publishStatus", 'runtime@' + _self.id)
                                _self.sendTo(name, "addListener", "publishState", 'runtime@' + _self.id)
                                _self.sendTo(name, "addListener", "getMethodMap", 'runtime@' + _self.id)

                                _self.sendTo(name, "broadcastState")
                                // below we subscribe to the Angular callbacks - where anything sent
                                // back from the webgui with our service's name on the message - send
                                // it to our onMsg method
                                var controller = arguments[0]
                                //console.log(this)
                                _self.subscribeToService(controller.onMsg, name)
                                // this is an 'optimization' - rather than subscribing to all service callbacks
                                // framework is only subscribing to one {name}.getMethodMap
                                _self.subscribeToServiceMethod(this.onMsg, name, 'getMethodMap')
                                // TODO - a method subscription who's callback is assigned here
                                // get methodMap
                                msgInterfaces[name].getMethodMap()
                                //                                console.log('here')
                            }
                        },
                        getMethodMap: function() {
                            _self.sendTo(name, "getMethodMap")
                        }
                    }
                }
                // Yikes ! circular reference ...
                // you can do sh*t like this in Js !
                // the point of this is to make a msg interface like
                // structure in the scope similar to the msg interface
                // we created for the controller - they start the same
                msgInterfaces[name].temp.msg = {}
                msgInterfaces[name].temp.msg._interface = msgInterfaces[name]
                //FIXME - hacked here @GroG: please look at this
                // _self.sendTo(_self.gateway.name, "subscribe", name, 'getMethodMap')
                _self.sendTo(name, "addListener", "getMethodMap", 'runtime@' + _self.id)
                // - name + '@' + _self.id)
                _self.subscribeToServiceMethod(msgInterfaces[name].onMsg, name, 'getMethodMap')
                msgInterfaces[name].getMethodMap()
                return deferred.promise
            },
            getPlatform: function() {
                return platform
            },
            getRemoteId: function() {
                return _self.remoteId
            },
            getRemotePlatform: function() {
                return remotePlatform
            },
            getId: function() {
                return _self.id
            },
            getPossibleServices: function() {
                return serviceTypes
            },
            setPossibleServices: function(types) {
                serviceTypes = types;
            },
            getRuntime: function() {
                // FIXME - this is wrong mrl.js is a js runtime service - this is just the runtime its connected to
                return runtime
            },
            getServicesFromInterface: function(name) {
                return _self.getServicesFromInterface(name)
            },
            getService: function(name) {
                return _self.getService(name)
            },
            updateState: function(service) {
                _self.addService(service)
            },
            init: function() {
                console.debug('mrl.init()')
                if (connected) {
                    console.debug('mrl.init() connected')
                    return true
                }
                if (connecting) {
                    console.debug('mrl.init() connecting')
                    return false
                }

                _self.connect()
                // return deferred.promise
            },
            isConnected: function() {
                return connected
            },

            noWorky: function(userId) {
                console.debug('mrl-noWorky', userId)
                _self.sendTo(runtime.name + '@' + _self.remoteId, "noWorky", userId)
            },
            getRegistry: function() {
                return registry
            },
            getServices: function() {

                var arrayOfServices = Object.keys(registry).map(function(key) {
                    return registry[key]
                })
                console.debug('mrl.getServices returned ' + arrayOfServices.length)
                return arrayOfServices
            },

            controllerscope: _self.controllerscope,
            setSearchFunction: _self.setSearchFunction,
            search: _self.search,
            createMessage: _self.createMessage,
            display: _self.display,
            getDisplayImages: getDisplayImages,
            setDisplayCallback: setDisplayCallback,
            subscribeToUpdates: _self.subscribeToUpdates,
            getPanelList: _self.getPanelList,
            sendTo: _self.sendTo,
            getShortName: _self.getShortName,
            getSimpleName: _self.getSimpleName,
            getStyle: _self.getStyle,
            subscribe: _self.subscribe,
            unsubscribe: _self.unsubscribe,
            subscribeToService: _self.subscribeToService,
            getFullName: _self.getFullName,
            sendBlockingMessage: _self.sendBlockingMessage,
            subscribeConnected: _self.subscribeConnected,
            subscribeToMethod: _self.subscribeToMethod,
            subscribeToServiceMethod: _self.subscribeToServiceMethod,
            getProperties: _self.getProperties,
            sendMessage: _self.sendMessage,
            setViewType: _self.setViewType,
            getViewType: _self.getViewType

        }

        let jsRuntime = {
            "creationCount": 0,
            "platform": {
                "os": "linux",
                "arch": "x86",
                "osBitness": 0,
                "jvmBitness": 64,
                "lang": "java",
                "vmName": "OpenJDK 64-Bit Server VM",
                "vmVersion": "1.8",
                "mrlVersion": "1.1.86",
                "isVirtual": true,
                "id": "local",
                "branch": "more_cli_fixes",
                "pid": "0",
                "hostname": "ctnal7a61203006",
                "commit": "da9bc9972307d58f9692187694d6f5446c895d74",
                "motd": "resistance is futile, we have cookies and robots ...",
                "startTime": "2019-12-10 16:10:37.605",
                "manifest": {
                    "Archiver-Version": "Plexus Archiver",
                    "Build-Host": "null",
                    "Build-Jdk": "1.8.0_192",
                    "Build-Time": "2019-06-20T13:26:29Z",
                    "Build-User": "root",
                    "Build-Version": "13",
                    "Built-By": "root",
                    "Created-By": "Apache Maven 3.6.0",
                    "GitBranch": "more_cli_fixes",
                    "GitBuildTime": "2019-06-20T06:26:31-0700",
                    "GitBuildUserEmail": "",
                    "GitBuildUserName": "",
                    "GitBuildVersion": "0.0.1-SNAPSHOT",
                    "GitClosestTagCommitCount": "",
                    "GitClosestTagName": "",
                    "GitCommitId": "da9bc9972307d58f9692187694d6f5446c895d74",
                    "GitCommitIdAbbrev": "da9bc99",
                    "GitCommitIdDescribe": "da9bc99",
                    "GitCommitIdDescribeShort": "da9bc99",
                    "GitCommitIdFull": "null",
                    "GitCommitTime": "2019-06-20T06:25:37-0700",
                    "GitCommitUserEmail": "grog@myrobotlab.org",
                    "GitCommitUserName": "GroG",
                    "GitDirty": "false",
                    "GitRemoteOriginUrl": "https://github.com/MyRobotLab/myrobotlab.git",
                    "GitTags": "",
                    "Implementation-Version": "1.1.86",
                    "Main-Class": "org.myrobotlab.service.Agent",
                    "Major-Version": "1.1.13",
                    "Manifest-Version": "1.0"
                }
            },
            "resources": {
                "totalPhysicalMemory": 0,
                "totalMemory": 240,
                "freeMemory": 225,
                "maxMemory": 3543
            },
            "jvmArgs": ["-agentlib:jdwp=transport=dt_socket,suspend=y,address=localhost:37551", "-javaagent:/lhome/grperry/Downloads/eclipse-jee-2019-06-R-linux-gtk-x86_64/eclipse/configuration/org.eclipse.osgi/405/0/.cp/lib/javaagent-shaded.jar", "-Dfile.encoding=UTF-8"],
            "args": ["--interactive", "--id", "local", "-s", "python", "Python", "--invoke", "python", "execFile", "./InMoov/InMoov.py"],
            "locale": "en-us",
            "serviceType": {
                "name": "org.myrobotlab.service.Runtime",
                "simpleName": "Runtime"
            },
            "name": "runtime",
            "id": "webgui-client-1234-5678",
            "simpleName": "Runtime",
            "serviceClass": "org.myrobotlab.service.Runtime",
            "isRunning": true,
            "interfaceSet": {
                "org.myrobotlab.client.Client$RemoteMessageHandler": "org.myrobotlab.client.Client$RemoteMessageHandler",
                "org.myrobotlab.framework.interfaces.MessageListener": "org.myrobotlab.framework.interfaces.MessageListener",
                "org.myrobotlab.service.interfaces.Gateway": "org.myrobotlab.service.interfaces.Gateway"
            },
            "statusBroadcastLimitMs": 1000,
            "isVirtual": false,
            "ready": true
        }

        // NOT READY FOR PRIMETIME - this needs to support js services in the future !
        // _self.addService(jsRuntime)

        service.init()
        return service
    }
    // END OF SERVICE !!!! --------------------------

    // assign callbacks
    this.request.onOpen = this.onOpen
    this.request.onClose = this.onClose
    this.request.onTransportFailure = this.onTransportFailure
    this.request.onMessage = this.onMessage
    this.request.onOpen = this.onOpen
    this.request.onError = this.onError
    this.id = generateId()

    // correct way to put in callbacks for js runtime instance
    jsRuntimeMethodMap["runtime@" + this.id + ".onRegistered"] = _self.onRegistered
    // jsRuntimeMethodMap["runtime@" + this.id + ".setServiceTypes"] = _self.setServiceTypes

    // FIXME - not sure if this callback map/notify entry will have multiple recievers - but
    // it was standardized with the others to do so
    methodCallbackMap['getHelloResponse'] = []
    methodCallbackMap['getHelloResponse'].push(_self.getHelloResponse)
    methodCallbackMap['onHelloResponse'] = []
    methodCallbackMap['onHelloResponse'].push(_self.onHelloResponse)

    // set callback for subscribeNameMethod["runtime@webgui-client-1234-5678"] = _self.onRegistered
    console.debug('mrl.js - end')
}
])
