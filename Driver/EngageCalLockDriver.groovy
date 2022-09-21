/**
 *  EngageCal Lock Driver
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

metadata {
    definition (name: "EngageCal Lock Driver", namespace: "EngageCal", author: "aerojsam", importUrl: "") {
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

/*>> DEVICE SETTINGS: LOCK >>*/
/* USED BY TRIGGER APP. TO ACCESS, USE parent.<setting>. */
Map deviceSettings() {
    return [
        1: [input: [name: "invertEngage", type: "bool", title: "Invert Engage/Disengage Operation (trigger disengage upon start of calendar event)", defaultValue: false]]
    ]
}
/*<< DEVICE SETTINGS: LOCK <<*/

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
            if (parent.invertEngage) {
                parent.scheduleDeviceEvent(deviceScheduledEvent.end, deviceScheduledEvent.start)
            } else {
                parent.scheduleDeviceEvent(deviceScheduledEvent.start, deviceScheduledEvent.end)
            }
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
    } else if (state.eventCurrentlyScheduled) {
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
    lock()
    parent.eventDevice?.lock()
}

def disengage() {
    unlock()
    parent.eventDevice?.unlock()
    state.eventCurrentlyScheduled = false
}

void lock() {
    sendEvent(name: "lock", value: "locked", descriptionText: "${device.displayName} is locked")
}

void unlock() {
    sendEvent(name: "lock", value: "unlocked", descriptionText: "${device.displayName} is unlocked")
}
