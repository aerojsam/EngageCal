def appVersion() { return "1.0.0" }

/**
 *  EngageCal
 *  https://raw.githubusercontent.com/aerojsam/Google_Calendar_Search/main/Apps/GCal_Search.groovy
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

definition(
    name: "EngageCal",
    namespace: "EngageCal",
    author: "aerojsam",
    description: "Schedule a device action using iCalendar events.",
    category: "Convenience",
    documentationLink: "",
    importUrl: "https://raw.githubusercontent.com/aerojsam/EngageCal/main/Apps/EngageCal.groovy",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {
    page(name: "mainPage", title: "", install: true, uninstall: true)
}

def installed() {
    //
    // Called once when the driver is created
    //
    try {
        logDebug("installed()");
        updated();
    }
    catch (Exception e) {
        logError("Exception in installed(): ${e}");
    }
}

def updated() {
    //
    // Called everytime the user saves the driver preferences
    //
    
    try {
        logDebug("updated()")

        // Clear previous states
        state.clear();

        // Unschedule possible previous runIn() calls
        unschedule()

        // Update driver version now and every Sunday @ 2am
        //versionUpdate();
        //schedule("0 0 2 ? * 1 *", versionUpdate);

        initialize()

        // Turn off debug log in 30 minutes
        if (logGetLevel() > 2) runIn(1800, logDebugOff);
    }
    catch (Exception e) {
        logError("Exception in updated(): ${e}");
    }
}

def mainPage() {
    dynamicPage(name: "mainPage") {
       

        def supportedDevices = MAP_SUPPORTED_DEVICES.collect{entry -> entry.value.capability_alias.capitalize()}
        
        section(getFormat("header-green", "${getImage("Blank")}"+" Getting Started")) {
            paragraph "Schedule device actions based on calendar events provided by an ICS link (URL). " +
                "Each device is tied to an <b>engage</b> and <b>disengage</b> action. "
            
            paragraph "By default, the <b>engage</b> action is triggered upon the start of a calendar event, and " +
                "the <b>disengage</b> action is triggered upon the end of a calendar event. " +
                "The engage/disengage order can be inverted by some EngageCal devices."
            
            paragraph "Supported Engage/Disengage Devices: "            
            supportedDevices.each {
                paragraph "\t- ${it}"
            }
            
            paragraph "Start by creating a new EngageCal device below."
        }

        section(getFormat("header-green", "${getImage("Blank")}"+" EngageCal Devices")) {
            app(name: "engageCalChildApp", appName: "EngageCal Child App", namespace: "EngageCal", title: "<b>Add a new EngageCal Device</b>", multiple: true)
        }
        
    }
}
