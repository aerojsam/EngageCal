/**
 *  EngageCal Child App
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

#include EngageCal.AppResources
#include EngageCal.CalendarResources

definition(
    name: "EngageCal Child App",
    namespace: "EngageCal",
    author: "aerojsam",
    description: "Schedule a device action using iCalendar events.",
    category: "Convenience",
	parent: "EngageCal:EngageCal",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/aerojsam/EngageCal/main/Apps/EngageCalChild.groovy",
)

preferences {
    page (name: "pageConfig")
    page (name: "spawnEngageCalDevice")
}

// App Life-Cycle Routines

def installed() {
    //
    // Called once when the app is created
    //
    
    logDebug("Installed with settings: ${settings}")
	initialize()
}

def updated() {
    //
    // Called everytime the user saves the driver preferences
    //
    
    logDebug("updated() settings: ${settings}")
    
	unschedule()
    
	initialize()
}

def initialize() {
    
    app.updateLabel("EngageCal ${state.deviceType?.value.capability_alias.capitalize()} App ${app.id}")
    
    if (!state.isPaused) {
        if ( settings.whenToRun == "Once Per Day" ) {
            schedule(settings.timeToRun, poll)
            logDebug("initialize - creating schedule once per day at: ${settings.timeToRun}")
        } else {
            def cronString = ""
            if ( settings.frequency == "Hours" ) {
                def hourlyTimeToRun = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", settings.hourlyTimeToRun)
                def hour = hourlyTimeToRun.hours
                def minute = hourlyTimeToRun.minutes
                cronString = "0 ${minute} ${hour}/${hours} * * ? *"
            } else if ( settings.frequency == "Minutes" ) {
                cronString = "0 0/${settings.minutes} * * * ?"
            } else if ( settings.frequency == "Cron String" ) {
                cronString = settings.cronString
            }
            schedule(cronString, poll)
            logDebug("initialize - creating schedule with cron string: ${cronString}")
        }
    }
}

void uninstalled() {
    //
    // Called once when the driver is deleted
    //
    try {
        // Delete all child devices
        List<com.hubitat.app.ChildDeviceWrapper> list = getChildDevices();
        if (list) list.each { deleteChildDevice(it.getDeviceNetworkId()); }

        logDebug("uninstalled()");
    }
    catch (Exception e) {
        logError("Exception in uninstalled(): ${e}");
    }
}

def poll() {
    def childDevice = getChildDevice(state.calendarDeviceID)
    logDebug "poll() - call child devices' refresh, which should be fetching/rendering new calendar data"
    
    try {
        // Delete all child devices
        List<com.hubitat.app.ChildDeviceWrapper> list = getChildDevices();
        if (list) list.each { it.refresh(); }
    }
    catch (Exception e) {
        logError("Exception in poll(): ${e}");
    }
}

// ----

def pageConfig() {
    return dynamicPage(name: "pageConfig", title: "Configuration", uninstall: true, nextPage: "spawnEngageCalDevice") {
        
        section("${getFormat("header-green", "${getImage("Blank")}"+" Select Device Type")}") {
            input "deviceType", "enum", title: "Select Device Type", multiple: false, required: true, submitOnChange: true, options: MAP_SUPPORTED_DEVICES.collect{entry -> entry.value.name}
            
            if (deviceType) {
                state.deviceType = MAP_SUPPORTED_DEVICES.find{it.value.name == deviceType}
                state.deviceEngageMap = state.deviceType?.value.engage
                state.deviceDisengageMap = state.deviceType?.value.disengage
                logDebug("engage Native Method: ${state.deviceEngageMap}")
                logDebug("disengage Native Method: ${state.deviceDisengageMap}")
            }
            
        }
        
        if (settings.deviceType) {
            section("${getFormat("header-green", "${getImage("Blank")}"+" Select Device")}") {
                logDebug("capability.${state.deviceType.value.capability_alias}")
                input "eventDevice", "capability.${state.deviceType.value.capability_alias}", title: "Select device to use:", required:true, multiple:false, submitOnChange:true
            }
        }
        
        if (settings.eventDevice) {
            section("${getFormat("header-green", "${getImage("Blank")}"+" Calendar Settings")}") {
                paragraph "${getFormat2("text", "Calendar refresh rate can be triggered once a day or periodically. Periodic options include every N hours, every N minutes, or you may enter a Cron expression.")}"
                input name: "whenToRun", type: "enum", title: "When to Run", required: true, options:["Once Per Day", "Periodically"], submitOnChange: true
                if ( settings.whenToRun == "Once Per Day" ) {
                    input name: "timeToRun", type: "time", title: "Time to run", required: true
                }
                if ( settings.whenToRun == "Periodically" ) {
                    input name: "frequency", type: "enum", title: "Frequency", required: true, options:["Hours", "Minutes", "Cron String"], submitOnChange: true
                    if ( settings.frequency == "Hours" ) {
                        input name: "hours", type: "number", title: "Every N Hours: (range 1-12)", range: "1..12", required: true, submitOnChange: true
                        input name: "hourlyTimeToRun", type: "time", title: "Starting at", defaultValue: "08:00", required: true
                    }
                    if ( settings.frequency == "Minutes" ) {
                        input name: "minutes", type: "enum", title: "Every N Minutes", required: true, options:["1", "2", "3", "4", "5", "6", "10", "12", "15", "20", "30"], submitOnChange: true
                    }
                    if ( settings.frequency == "Cron String" ) {
                        paragraph "${getFormat2("text", "If not familiar with Cron Strings, please visit <a href='https://www.freeformatter.com/cron-expression-generator-quartz.html#' target='_blank'>Cron Expression Generator</a>")}"
                        input name: "cronString", type: "text", title: "Enter Cron string", required: true, submitOnChange: true
                    }
                }
                paragraph "${getFormat2("line")}"
            }
        }
        
        if (settings.whenToRun) {
            section("${getFormat("header-green", "${getImage("Blank")}"+" Calendar Preferences (Optional)")}") {
                
                def event_offset_paragraph = """
                <u>Event Offset Preferences</u>:
                - By default, devices engage/disengage based on the start/end of the calendar event, respectively.
                - All-Day calendar events start and end at 00:00 AM (midnight) of that day.
                - Use the settings below to offset the start/end of the device engage/disengage event, relative to the calendar event.

                Examples:
                - <u>Schedule device engage at 14:30:00, relative to the start of the calendar event</u>
                    Enter "870" in "Event Start Offset in minutes (+/-)" Field
                - <u>Schedule device disengage at 14:00:00, relative to the end of the calendar event</u>
                    Enter "840" in "Event End Offset in minutes (+/-)" Field
                """.stripIndent()
                
                paragraph "${getFormat2("text", "${event_offset_paragraph}")}"
                input name: "setOffset", type: "bool", title: "Set offset?", defaultValue: false, required: false, submitOnChange: true
                if ( settings.setOffset == true ) {
                    input name: "offsetStart", type: "decimal", title: "Event Start Offset in minutes (+/-)", required: false
                    input name: "offsetEnd", type: "decimal", title: "Event End Offset in minutes (+/-)", required: false
                }
                paragraph "${getFormat2("line")}"
            }
        }
        
        if ( state.installed ) {
            section ("<b>Uninstall Instructions</b>") {
                paragraph "ATTENTION: The only way to uninstall this trigger and the corresponding child device is by clicking the Remove button below. Trying to uninstall the corresponding device from within that device's preferences will NOT work."
            }
        }
        
    }
}

def spawnEngageCalDevice() {
    logDebug("spawnEngageCalDevice()")
    
    state.calendarDeviceID = "EngageCal_${app.id}"
    def calendarDevice = getChildDevice(state.calendarDeviceID)
    
    if (!calendarDevice) {
        def driverName = "EngageCal ${state.deviceType.value.name.capitalize()} Driver"
        def deviceName = "EngageCal ${state.deviceType.value.name.capitalize()} ${app.id}"
        
        logDebug("Create device \"${deviceName}\" of driver \"${driverName}\"")
        
        calendarDevice = addChildDevice("EngageCal", driverName, state.calendarDeviceID, null, [name: driverName, label: deviceName])
    }
    
    return dynamicPage(name: "deviceSettings", title: "${parent.getFormat("title", "Device Settings")}", install: true, uninstall: true, nextPage: "" ) {
        section ("${getFormat("header-green", "${getImage("Blank")}"+" Settings")}") {
            calendarDevice?.deviceSettings().each {
                input it.value.input
            }
        }
    }
}

def scheduleDeviceEvent(engageScheduledTime, disengageScheduledTime) {
    def calendarDevice = getChildDevice(state.calendarDeviceID)
    def testMode = calendarDevice.currentValue("testMode")
    log.info "aaa ${calendarDevice.testMode}"
    
    logInfo("Test Mode? ${testMode}")
    
    if (testMode) {
        scheduleDeviceEventTestMode()
        return 0
    }
    
    logInfo("Schedule Engage ${engageScheduledTime}") 
    logInfo("Schedule Disengage ${disengageScheduledTime}")
    
    runOnce(engageScheduledTime, engage)
    runOnce(disengageScheduledTime, disengage)
}

def scheduleDeviceEventTestMode()
{
    logInfo("-- INITIATING TEST MODE --")
    logInfo("Invert Engage? ${invertEngage}")
    
    /* TEST PURPOSES ONLY:
     * Create a fake event that will start TODAY in 10 seconds
     */
    
    def scheduleStartTime = null
    def scheduleEndTime = null
    
    use (TimeCategory) {
        scheduleStartTimeDebug = new Date()
        scheduleStartTimeDebug += 10.seconds // starting event in 10 seconds
        
        scheduleStartTime = scheduleStartTimeDebug
    }
    
    /* TEST PURPOSES ONLY:
     * Create a fake event that will end by TODAY in 20 seconds
     */
    
    use (TimeCategory) {
        scheduleEndTimeDebug = scheduleStartTimeDebug
        scheduleEndTimeDebug += 20.seconds // starting event in 10 seconds
        
        scheduleEndTime = scheduleEndTimeDebug
    }
    
    if (invertEngage) {
        logInfo("Schedule Engage At ${scheduleEndTime}")
        logInfo("Schedule Disengage At ${scheduleStartTime}")
        runOnce(scheduleEndTime, engage)
        runOnce(scheduleStartTime, disengage)
    } else {
        logInfo("Schedule Engage At ${scheduleStartTime}")
        logInfo("Schedule Disengage At ${scheduleEndTime}")
        runOnce(scheduleStartTime, engage)
        runOnce(scheduleEndTime, disengage)
    }
    
    logInfo("-- END OF TEST MODE --")
}

def engage(data) {
    //log.debug("engage - ${state.deviceType}")
    def calendarDevice = getChildDevice(state.calendarDeviceID)
    calendarDevice.engage()
}

def disengage(data) {
    //logDebug("disengage - ${state.deviceType}")
    def calendarDevice = getChildDevice(state.calendarDeviceID)
    calendarDevice.disengage()
}
