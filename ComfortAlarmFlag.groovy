/**
 *
 * UPNP Comfort Alarm Flag Interface
 * Copyright Matt Brain (matt.brain@gmail.com)
 * https://github.com/mattbrain/Cytech-Comfort
 *
 *
 *
 */
metadata {
	definition (name: "ComfortAlarm Flag", namespace: "mattbrain", author: "Matt Brain") {
    	capability "Actuator"
    	capability "Switch"
        capability "Refresh"
        capability "Sensor"
        
        attribute "currentIP", "string"
        attribute "maker", "string"
        
        command "subscribe"
        command "unsubscribe"
        command "processEvent"
        command "makerToggle"
        command "on"
        command "off"
        command "toggle"
	}

	simulator {
		// TODO: define status and reply messages here
	}

    
	tiles (scale: 2) {
  		multiAttributeTile(name:"rich-control", type: "switch", canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                 attributeState "on", label:'${name}', action:"off", icon:"st.illuminance.illuminance.light", backgroundColor:"#79b821", nextState:"turningOff"
                 attributeState "off", label:'${name}', action:"on", icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "turningOn", label:'${name}', action:"off", icon:"st.illuminance.illuminance.light", backgroundColor:"#79b821", nextState:"turningOff"
                 attributeState "turningOff", label:'${name}', action:"on", icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff", nextState:"turningOn"
  				attributeState "offline", label:'${name}', icon:"st.illuminance.illuminance.dark", backgroundColor:"#ff0000"
                attributeState "unknown", label:'${name}', icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff"
 			}
        }

        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label:'${name}', action:"off", icon:"st.illuminance.illuminance.light", backgroundColor:"#79b821", nextState:"turningOff"
            state "off", label:'${name}', action:"on", icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff", nextState:"turningOn"
            state "turningOn", label:'${name}', action:"off", icon:"st.illuminance.illuminance.light", backgroundColor:"#79b821", nextState:"turningOff"
            state "turningOff", label:'${name}', action:"on", icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff", nextState:"turningOn"
    		state "offline", label:'${name}', icon:"st.illuminance.illuminance.dark", backgroundColor:"#ff0000"
            state "unknown", label:'${name}', icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff"
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Refresh", action:"refresh", icon:"st.secondary.refresh"
        }
        standardTile("on", "device.switchon", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Turn On", action:"on", icon:"st.illuminance.illuminance.light"
        }
        standardTile("off", "device.switchoff", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Turn Off", action:"off", icon:"st.illuminance.illuminance.dark"
        }
        standardTile("toggle", "device.switchflashonce", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Toggle", action:"toggle", icon:"st.illuminance.illuminance.light"
        }
        standardTile("maker", "device.maker", height: 2, width: 2, decoration: "flat") {
            state("on", label:'On', nextState: "pending", action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#00ff00")
            state("off", label:'Off', nextState: "pending", action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#ffffff")
            state("pending", label:'waiting',action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#ff0000")
        }
		main "switch"
		details (["rich-control", "on", "refresh", "maker", "off"])
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "->parse ${description}"
    def msg = parseLanMessage(description)
	def headerString = msg.header
    def bodyString = msg.body
    log.debug "Parsed: ${headerString}, ${bodyString}"
    def notifyString = getChildFromHeader(headerString)
    def sid = getSIDFromHeader(headerString)
    if (sid) {
    	// got a SID in the data, therefore must be a ack for request / renew
        if (getDataValue("sid")) {
        	if (getDataValue("sid")==sid){
            	log.debug "New sid = old sid - therefore renew worked"
            } else {
            	log.debug "New sid != old sid- therefore new subscription"
                unsubscribe(getDataValue("sid"))
                updateDataValue("sid",sid)
            }
        } else { 
        	log.debug "New sid, no existing SID in data"
        	updateDataValue("sid",sid)
            sendEvent(name: "switch", value: "unknown", descriptionText: "Waiting on event")
        }
        updateDataValue("lastSIDTime",Long.toString(now()))
    } else if (getHTTPStatusFromHeader(headerString)!="200") {
    	if (getDataValue("unsubscribe")) {
        	updateDataValue("unsubscribe","")
        } else {
    		log.debug "HTTP Error in parse ${getHTTPStatusFromHeader(headerString)}"
    		updateDataValue("sid","")
        	sendEvent(name: "switch", value: "offline", descriptionText: "Subscription Error")
        }
    }
}

def processEvent(sid, item, value) {
	log.debug "->processEvent ${sid}, ${item}, ${value}"
	if (sid==getDataValue("sid")) {
    	log.debug "->processEvent: Correct SID"
		if (item == "Status") {
        	log.debug "->processEvent: State Update"
        	if (value=="0") {
            	value="off"
            } else {
            	value="on"
            }
            log.debug "->processEvent: Setting switch to ${value}"
    		sendEvent(name: "switch", value: value, descriptionText: "Switch is ${value}")
    	} else if (item=="Maker") {
        	if (value=="0") {
            	updateDataValue("maker","Off")
            	value="off"
            } else {
            	value="on"
                updateDataValue("maker","On")
            }
            log.debug "->processEvent: Setting Maker to ${value}"
            sendEvent(name: "maker", value: value, descriptionText: "Maker is ${value}")
       }
    } else {
    	log.debug "->process event with incorrect sid, unsubscribing"
        upnpunsubscribe(sid)
	}
} 

void subscribe() {
	def lastSIDTime = 0
	if (getDataValue("lastSIDTime"))
    	{lastSIDTime = Long.parseLong(getDataValue("lastSIDTime"))}
    log.debug "->subscribe(), last subscription acknowledge ${(now() - lastSIDTime)/1000} seconds ago"
    
	if ((now() - lastSIDTime) > 604800000) {
    	// previous SID has expired, request new one
        log.debug("Clearing out expired subscription")
		updateDataValue("sid","")
        sendEvent(name: "switch", value: "offline", descriptionText: "Subscription Expired")
   		// update schedule
        schedule("10 0/5 * * * ?",subscribe) 
    }
	def address = getCallBackAddress()
    def headers
    if (getDataValue("sid")) {
    	log.debug "Renew Subscription"
        headers = [
        	HOST: getDataValue("ip") + ":" + getDataValue("port"),
			SID: getDataValue("sid"),
            TIMEOUT: "Second-604800"
           ]
    		
    } else {
    	
    	log.debug "New Subscription"
    	headers = [
        	HOST: getDataValue("ip") + ":" + getDataValue("port"),
            CALLBACK: "<http://${address}/notify/${this.device.deviceNetworkId}>",
            NT: "upnp:event",
            TIMEOUT: "Second-604800"
           ]
           sendEvent(name: "switch", value: "offline", descriptionText: "Subscription Requested")
    }
    def action = new physicalgraph.device.HubAction(
    	method: "SUBSCRIBE",
        path: getDataValue("subURL"),
        headers: headers
    )
    def result = sendHubCommand(action)
    log.debug "SUBSCRIBE ${suburl}: ${action}: ${result}" 
}

def upnpunsubscribe(sid) {
	log.debug "->Unsubscribing ${sid}"
    def headers = [
    HOST: getDataValue("ip") + ":" + getDataValue("port"),
	SID: sid
    ]
    def action = new physicalgraph.device.HubAction(
    	method: "UNSUBSCRIBE",
        path: getDataValue("subURL"),
        headers: headers
    )
    def result = sendHubCommand(action)
    updateDataValue("unsubscribe", "true")
}

def setOffline() {
    sendEvent(name: "switch", value: "offline", descriptionText: "The device is offline")
}

private Integer convertHexToInt(hex) {
 	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
 	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

def sync(ip, port) {
	def existingIp = getDataValue("ip")
	def existingPort = getDataValue("port")
	if (ip && ip != existingIp) {
		updateDataValue("ip", ip)
	}
	if (port && port != existingPort) {
		updateDataValue("port", port)
	}
}

private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}


private getChildFromHeader(header) {
	def notifyPath = null
    if (header?.contains("NOTIFY")) {
		notifyPath = (header =~ /NOTIFY.*/)[0]
        notifyPath = notifyPath.split()[1]
        notifyPath -= "/notify/".trim()
    }
    return notifyPath
}

private getSIDFromHeader(header) {
	def sid = null
	if (header?.contains("SID: uuid:")) {
		sid = ( header =~ /SID: uuid:.*/)[0]
		sid -= "SID: ".trim()
	}
    return sid
}

private getHTTPStatusFromHeader(header) {
	def httpStatus = null
    if (header?.contains("HTTP/1.1")) {
		httpStatus = (header =~ /HTTP.*/)[0]
        httpStatus = httpStatus.split()[1]
    }
    return httpStatus
}

void on() {
	log.debug "->SwitchOn()"
	doAction("SetStatus",[NewStatusValue:1])
}


void off() {
	log.debug "->SwitchOff()"
    doAction("SetStatus",[NewStatusValue:0])
}


void makerToggle() {
	log.debug "->makerToggle"
    //sendEvent(name: "maker", value: "on", descriptionText: "Waiting for a response from the device", displayed: true)
	if (getDataValue("maker")=="Off") {
        doAction("SetMaker", [NewMakerValue:1])
    } else {
    	updateDataValue("maker","Off")
        doAction("SetMaker", [NewMakerValue:0])
    }
    log.debug "Sending sendEvent"
	//sendEvent(name: "maker", value: "pending", descriptionText: "Waiting for a response from the device", displayed: true)
}

void refresh() {
	log.debug "Manual Subscription renewal"
    renewSubscription() 
}


void renewSubscription() {
	log.debug "Subscription Renewal"
    updateDataValue("sid","")
    subscribe()
    schedule("10 0/5 * * * ?",subscribe) 
}


def doAction(action, Map body = [InstanceID:0, Speed:1]) {
	log.debug "doAction ${action}, ${body}"
    def result = new physicalgraph.device.HubSoapAction(
        path:    getDataValue("controlURL"),
        urn:     "urn:www.cytech.com:service:flag:1",
        action:  action,
        body:    body,
        headers: [Host:getDataValue("ip") + ":" + getDataValue("port"), CONNECTION: "close"]
    )
    //return result
    sendHubCommand(result)
}