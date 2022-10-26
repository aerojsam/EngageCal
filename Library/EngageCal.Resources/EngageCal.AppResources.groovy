library (
        author: "aerojsam",
        category: "Utils",
        description: "App Resources",
        name: "EngageCal.AppResources",
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

void updateAttr(String aKey, aValue){
    sendEvent(name:aKey, value:aValue)
}

void updateAttr(String aKey, aValue, aUnit){
    sendEvent(name:aKey, value:aValue, unit:aUnit)
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

// Attribute handling ---------------------------------------------------------------------------------------------------------

private Boolean attributeUpdateString(String val, String attribute) {
  //
  // Only update "attribute" if different
  // Return true if "attribute" has actually been updated/created
  //
  if ((device.currentValue(attribute) as String) != val) {
    sendEvent(name: attribute, value: val);
    return (true);
  }

  return (false);
}

// ------------------------------------------------------------

// Versioning -----------------------------------------------------------------------------------------------------------------
public static String version() { return "v1.0.2"; }
public static String gitHubUser() { return "aerojsam"; }
public static String gitHubRepo() { return "EngageCal"; }
public static String gitHubBranch() { return "main"; }

private Map versionExtract(String ver) {
  //
  // Given any version string (e.g. version 2.5.78-prerelease) will return a Map as following:
  //   Map.major version
  //   Map.minor version
  //   Map.build version
  //   Map.desc  version
  // or "null" if no version info was found in the given string
  //
  Map val = null;

  if (ver) {
    String pattern = /.*?(\d+)\.(\d+)\.(\d+).*/;
    java.util.regex.Matcher matcher = ver =~ pattern;

    if (matcher.groupCount() == 3) {
      val = [:];
      val.major = matcher[0][1].toInteger();
      val.minor = matcher[0][2].toInteger();
      val.build = matcher[0][3].toInteger();
      val.desc = "v${val.major}.${val.minor}.${val.build}";
    }
  }

  return (val);
}

Boolean versionUpdate() {
  //
  // Return true is a new version is available
  //
  logDebug("versionUpdate()");

  Boolean ok = false;
  String attribute = "driver";

  try {
    // Retrieve current version
    Map verCur = versionExtract(version());
    if (verCur) {
      // Retrieve latest version from GitHub repository manifest
      // If the file is not found, it will throw an exception
      Map verNew = null;
      String manifestText = "https://raw.githubusercontent.com/${gitHubUser()}/${gitHubRepo()}/${gitHubBranch()}/packageManifest.json".toURL().getText();
      if (manifestText) {
        // text -> json
        Object parser = new groovy.json.JsonSlurper();
        Object manifest = parser.parseText(manifestText);

        verNew = versionExtract(manifest.version);
        if (verNew) {
          // Compare versions
          if (verCur.major > verNew.major) verNew = null;
          else if (verCur.major == verNew.major) {
            if (verCur.minor > verNew.minor) verNew = null;
            else if (verCur.minor == verNew.minor) {
              if (verCur.build >= verNew.build) verNew = null;
            }
          }
        }
      }

      String version = verCur.desc;
      if (verNew) version = "<font style='color:#3ea72d'>${verCur.desc} (${verNew.desc} available)</font>";
      ok = attributeUpdateString(version, attribute);
    }
  }
  catch (Exception e) {
    logError("Exception in versionUpdate(): ${e}");
  }

  return (ok);
}
// --------------------------------------------------------------------------------------------------------------------
