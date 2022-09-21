/**
 *  EngageCal LockCode Driver
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

#include EngageCal.AppResources
#include EngageCal.CalendarResources

metadata {
    definition (name: "EngageCal Lockcode Driver", namespace: "EngageCal", author: "aerojsam", importUrl: "") {
        capability "Refresh"
        capability "Actuator"
        capability "Lock"
		capability "Lock Codes"
        attribute "calendarView", "text"
        attribute "eventStart", "text"
        attribute "eventEnd", "text"
        attribute "eventDescripton", "text"
        attribute "lastUpdated", "text"
        attribute "testMode", "bool"
        command "refresh"
        command "clearEventCache"
    }
}

preferences {
    input(name: "icslink", type: "text", title: "<font style='font-size:15px; color:#1a77c9'>Insert ICS URL</font>", description: "<font style='font-size:12px; font-style: italic'>Insert URL of .ics calendar here</font>", required: true, submitOnChange: true)
    input(name: "logLevel", type: "enum", title: "<font style='font-size:12px; color:#1a77c9'>Log Verbosity</font>", description: "<font style='font-size:12px; font-style: italic'>Default: 'Debug' for 30 min and 'Info' thereafter</font>", options: [0:"Error", 1:"Warning", 2:"Info", 3:"Debug", 4:"Trace"], multiple: false, defaultValue: 3, required: true);
    input(name: "testMode", type: "bool", title: "<font style='font-size:12px; color:#1a77c9'>Test Mode</font>", description: "<font style='font-size:12px; font-style: italic'>When enabled, performs engage/disengage test (order depends on invertEngage setting). Starts in 10 seconds and ends in 20 seconds. </font>", required: true, submitOnChange: true);
}

/*>> DEVICE SETTINGS: LOCKCODE >>*/
/* USED BY TRIGGER APP. TO ACCESS, USE parent.<setting>. */
Map deviceSettings() {
    return [
        1: [input: [name: "defaultLockCodePosition", type: "number", title: "Default Lock Position [0 to 20]", description: "[0 - 20]", range: "0..20", defaultValue: "0"], required: true, submitOnChange: true, parameterSize: 1],
        2: [input: [name: "defaultLockCodePIN", type: "number", title: "Default Lock Code PIN (used only if not provided in calendar event)", description: "1234 (only 4-digit codes supported)", range: "1000..9999", defaultValue: "1234"], required: true, submitOnChange: true, parameterSize: 1],
        3: [input: [name: "defaultLockCodeName", type: "text", title: "Default Lock Code Name", defaultValue: "EngageCal"], required: true, submitOnChange: true],
    ]
}
/*<< DEVICE SETTINGS: LOCKCODE <<*/

def installed() {
    logDebug("installed()")
    unschedule()
    refresh()
    
    state.eventCurrentlyScheduled = false
}

def updated() {
    logDebug("updated()")
    unschedule()
    refresh()

    // Update driver version now and every Sunday @ 2am
    versionUpdate();
    schedule("0 0 2 ? * 1 *", versionUpdate);
}

def refresh() {
    logDebug("refresh()")

    if (testMode) {
        sendEvent(name: "testMode", value: true)
    } else {
        sendEvent(name: "testMode", value: false)
    }
    
    if(icslink && !state.eventCurrentlyScheduled) {
        (eventOffsetStartSeconds, eventOffsetEndSeconds) = parent.getEventOffsetsInSeconds()
        log.info("Process Event For Today. eventOffsetStartSeconds ${eventOffsetStartSeconds} eventOffsetEndSeconds ${eventOffsetEndSeconds}")
        deviceScheduledEvent = iCalProcessTodayEvent(icslink, eventOffsetStartSeconds, eventOffsetEndSeconds)
        
        if (deviceScheduledEvent && !testMode) {
            // schedule device event
            parent.scheduleDeviceEvent(deviceScheduledEvent.start, deviceScheduledEvent.end)
            state.lockCode = getPINFromCalendarEvent(deviceScheduledEvent.description)
            state.eventCurrentlyScheduled = true
        } else if (testMode) {
            parent.scheduleDeviceEventTestMode()
            state.eventCurrentlyScheduled = true
        } else {
            state.eventCurrentlyScheduled = false
        }

        logInfo("refresh() - icslink: ${icslink}")
        
        calendarHtmlView = CalendarRenderViewFromICS(icslink)
        
        sendEvent(name: "lastUpdated", value: new Date())
        sendEvent(name: "calendarView", value: calendarHtmlView)
        sendEvent(name: "eventStart", value: deviceScheduledEvent.start ? deviceScheduledEvent.start : "No Calendar Events Today")
        sendEvent(name: "eventEnd", value: deviceScheduledEvent.end ? deviceScheduledEvent.end : "No Calendar Events Today")
        sendEvent(name: "eventDescripton", value: deviceScheduledEvent.description ? deviceScheduledEvent.description : "None")
    } else if(state.eventCurrentlyScheduled) {
        logInfo("refresh() - Device Event Currently Scheduled!")
    } else {
        logInfo("refresh() - ICS URL Link Not Provided!")
    }
}

def clearEventCache() {
    unschedule()
    updateAttr("lastUpdated", "")
    updateAttr("calendarView", "")
    updateAttr("eventStart", "")
    updateAttr("eventEnd", "")
    updateAttr("eventDescripton", "")
    updateAttr("testMode", false)
    state.eventCurrentlyScheduled = false
}

def engage() {
    def lockCode = parent.defaultLockCodePIN.toString()
    
    // if unable to extract PIN from calendar event, stay with (user-configured) default
    if (state.lockCode) {
        lockCode = state.lockCode
    }
    
    setCode(parent.defaultLockCodePosition, lockCode, parent.defaultLockCodeName)
    parent.eventDevice?.setCode(parent.defaultLockCodePosition, lockCode, parent.defaultLockCodeName)
}

def disengage() {
    deleteCode(parent.defaultLockCodePosition)
    parent.eventDevice?.deleteCode(parent.defaultLockCodePosition)
    state.lockCode = null
    state.eventCurrentlyScheduled = false
}

/*>> LOCK CODE HELPERS >>*/
Boolean changeIsValid(lockCodes,codeMap,codeNumber,code,name){
    //validate proposed lockCode change
    Boolean result = true
    Integer maxCodeLength = device.currentValue("codeLength")?.toInteger() ?: 4
    Integer maxCodes = device.currentValue("maxCodes")?.toInteger() ?: 20
    Boolean isBadLength = code.size() > maxCodeLength
    Boolean isBadCodeNum = maxCodes < codeNumber
    if (lockCodes) {
        List nameSet = lockCodes.collect{ it.value.name }
        List codeSet = lockCodes.collect{ it.value.code }
        if (codeMap) {
            nameSet = nameSet.findAll{ it != codeMap.name }
            codeSet = codeSet.findAll{ it != codeMap.code }
        }
        Boolean nameInUse = name in nameSet
        Boolean codeInUse = code in codeSet
        if (nameInUse || codeInUse) {
            if (nameInUse) { logWarning("changeIsValid:false, name:${name} is in use:${ lockCodes.find{ it.value.name == "${name}" } }") }
            if (codeInUse) { logWarning("changeIsValid:false, code:${code} is in use:${ lockCodes.find{ it.value.code == "${code}" } }") }
            result = false
        }
    }
    if (isBadLength || isBadCodeNum) {
        if (isBadLength) { logWarning("changeIsValid:false, length of code ${code} does not match codeLength of ${maxCodeLength}") }
        if (isBadCodeNum) { logWarning("changeIsValid:false, codeNumber ${codeNumber} is larger than maxCodes of ${maxCodes}") }
        result = false
    }
    return result
}

Map getCodeMap(lockCodes, codeNumber){
    Map codeMap = [:]
    Map lockCode = lockCodes?."${codeNumber}"
    if (lockCode) {
        codeMap = ["name":"${lockCode.name}", "code":"${lockCode.code}"]
    }
    return codeMap
}

Map getLockCodes() {
    /*
	on a real lock we would fetch these from the response to a userCode report request
	*/
    String lockCodes = device.currentValue("lockCodes")
    Map result = [:]
    if (lockCodes) {
        //decrypt codes if they're encrypted
        if (lockCodes[0] == "{") result = new JsonSlurper().parseText(lockCodes)
        else result = new JsonSlurper().parseText(decrypt(lockCodes))
    }
    return result
}

void getCodes() {
    //no op
}

void updateLockCodes(lockCodes){
    /*
	whenever a code changes we update the lockCodes event
	*/
    logDebug("updateLockCodes: ${lockCodes}")
    String strCodes = JsonOutput.toJson(lockCodes)
    if (optEncrypt) {
        strCodes = encrypt(strCodes)
    }
    sendEvent(name:"lockCodes", value:strCodes, isStateChange:true)
}

void updateEncryption(){
    /*
	resend lockCodes map when the encryption option is changed
	*/
    String lockCodes = device.currentValue("lockCodes") //encrypted or decrypted
    if (lockCodes){
        if (optEncrypt && lockCodes[0] == "{") {	//resend encrypted
            sendEvent(name:"lockCodes",value: encrypt(lockCodes))
        } else if (!optEncrypt && lockCodes[0] != "{") {	//resend decrypted
            sendEvent(name:"lockCodes",value: decrypt(lockCodes))
        }
    }
}

String getPINFromCalendarEvent(eventDescription)
{
    eventReservationURL = null
    lockCodePIN = null
    
    logDebug("Extract PIN from event description: \"${eventDescription}\"")

    pattern = ~/Phone.*:(.*)/
    matcher = (eventDescription =~ pattern).findAll()
    lockCodePIN = matcher[0][1]
    lockCodePIN = lockCodePIN.trim()

    logDebug("eventReservationURL ${eventReservationURL}")
    logDebug("lockCodePIN ${lockCodePIN}")
    
    return lockCodePIN
}
/*<< LOCK CODE HELPERS <<*/

/*>> LOCK CODE CAPABILITY HANDLERS >>*/
void setCode(codeNumber, code, name = null) {
    /*
	on sucess
		name		value								data												notes
		codeChanged	added | changed						[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]	default name to code #<codeNumber>
		lockCodes	JSON map of all lockCode
	*/
 	if (codeNumber == null || code == null) return

    logDebug("setCode- ${codeNumber}")
	
    if (!name) name = "code #${codeNumber}"

    Map lockCodes = getLockCodes()
    logDebug("lockCodes- ${lockCodes}")
    
    Map codeMap = getCodeMap(lockCodes,codeNumber)
    if (!changeIsValid(lockCodes, codeMap, codeNumber, code, name)) return
	
   	Map data = [:]
    String value
	
    logInfo("setting code ${codeNumber} to ${code} for lock code name ${name}")

    if (codeMap) {
        if (codeMap.name != name || codeMap.code != code) {
            codeMap = ["name":"${name}", "code":"${code}"]
            lockCodes."${codeNumber}" = codeMap
            data = ["${codeNumber}":codeMap]
            if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
            value = "changed"
        }
    } else {
        codeMap = ["name":"${name}", "code":"${code}"]
        data = ["${codeNumber}":codeMap]
        lockCodes << data
        if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        value = "added"
    }
    updateLockCodes(lockCodes)
    sendEvent(name:"codeChanged", value:value, data:data, isStateChange: true)
}

void deleteCode(codeNumber) {
    /*
	on sucess
		name		value								data
		codeChanged	deleted								[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]
		lockCodes	[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"],<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]
	*/
    Map lockCodes = getLockCodes()
    Map codeMap = getCodeMap(lockCodes, "${codeNumber}")
    if (codeMap) {
		Map result = [:]
        //build new lockCode map, exclude deleted code
        lockCodes.each{
            if (it.key != "${codeNumber}"){
                result << it
            }
        }
        updateLockCodes(result)
        Map data =  ["${codeNumber}":codeMap]
        //encrypt lockCode data is requested
        if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        
        logInfo("delete code ${code} from position ${codeNumber}")
        sendEvent(name:"codeChanged", value:"deleted", data:data, isStateChange: true)
    }
}
