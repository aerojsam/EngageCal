library (
        author: "aerojsam",
        category: "Apps",
        description: "App Resources",
        name: "AppResources",
        namespace: "EngageCal",
        documentationLink: ""
)

import groovy.json.*
import hubitat.helper.RMUtils
import java.util.TimeZone
import groovy.transform.Field
import groovy.time.TimeCategory
import java.text.SimpleDateFormat

@Field static Map MAP_SUPPORTED_DEVICES=[
    1: [name:"switch", capability_alias: "switch", engage: [method: "on", state: "on"], disengage: [method: "off", state: "off"]],
    2: [name:"lock", capability_alias: "lock", engage: [method: "lock", state: "locked"], disengage: [method: "off", state: "unlocked"]],
    3: [name:"lockcode", capability_alias: "lockCodes", engage: [method: "setCode", state: null], disengage: [method: "deleteCode", state: null]]
    ]

def getImage(type) {					// Modified from @Stephack Code
    def loc = "<img src=https://github.com/aerojsam/EngageCal/blob/main/Assets/Images/"
    if(type == "Blank") return "${loc}blank.png height=40 width=5}>"
}

def getFormat(type, myText="") {			// Modified from @Stephack Code
    if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def getFormat2(type, displayText=""){ // Modified from @Stephack and @dman2306 Code   
    def color = "#1A77C9"
    if(type == "title") return "<h2 style='color:" + color + ";font-weight:bold'>${displayText}</h2>"
    if(type == "box") return "<div style='color:white;text-align:left;background-color:#1A77C9;padding:2px;padding-left:10px;'><h3><b><u>${displayText}</u></b></h3></div>"
    if(type == "text") return "<span style='font-size: 14pt;'>${displayText}</span>"
    if(type == "line") return "<hr style='background-color:" + color + "; height: 1px; border: 0;'>"
}

Integer logGetLevel() {
  //
  // Get the log level as an Integer:
  //
  //   0) log only Errors
  //   1) log Errors and Warnings
  //   2) log Errors, Warnings and Info
  //   3) log Errors, Warnings, Info and Debug
  //   4) log Errors, Warnings, Info, Debug and Trace/diagnostic (everything)
  //
  // If the level is not yet set in the driver preferences, return a default of 2 (Info)
  // Declared public because it's being used by the child-devices as well
  //
  if (settings.logLevel != null) return (settings.logLevel.toInteger());
  return (2);
}

// Logging --------------------------------------------------------------------------------------------------------------------
void logDebugOff() {
  //
  // runIn() callback to disable "Debug" logging
  // Cannot be private
  //
  if (logGetLevel() > 2) device.updateSetting("logLevel", [type: "enum", value: "2"]);
}

void logError(String str) { log.error(str); }
void logWarning(String str) { if (logGetLevel() > 0) log.warn(str); }
void logInfo(String str) { if (logGetLevel() > 1) log.info(str); }
void logDebug(String str) { if (logGetLevel() > 2) log.debug(str); }
void logTrace(String str) { if (logGetLevel() > 3) log.trace(str); }

void logData(Map data) {
  //
  // Log calendar data as Map
  // Used only for diagnostic/debug purposes
  //
  if (logGetLevel() > 3) {
    data.each {
      logTrace("$it.key = $it.value");
    }
  }
}
// --------------------------------------------------------------------------------------------------------------------
