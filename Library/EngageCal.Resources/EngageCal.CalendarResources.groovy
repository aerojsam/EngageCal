library (
        author: "aerojsam",
        category: "Utils",
        description: "iCalendar Helpers",
        name: "CalendarResources",
        namespace: "EngageCal",
        documentationLink: ""
)

import groovy.json.*
import hubitat.helper.RMUtils
import java.util.TimeZone
import groovy.transform.Field
import groovy.time.TimeCategory
import java.text.SimpleDateFormat

import java.util.regex.*
    
@Field static String REGEX_ICAL=/^(BEGIN:VEVENT.+?DTEND.+?:(.+?)DTSTART.+?:(.+?)UID:(.+?)DESCRIPTION:(.+?)SUMMARY:(.+?)END:VEVENT)$/

def iCalParse(ical_raw) {
    HashMap iCalMap = [:]
    Integer eventCount = 0
    iCalMap.put("event",[:])
    
    //log.debug "${ical_raw}"
    
    Pattern pattern = Pattern.compile(REGEX_ICAL, Pattern.MULTILINE | Pattern.COMMENTS | Pattern.UNICODE_CHARACTER_CLASS | Pattern.DOTALL);
    Matcher matcher = pattern.matcher(ical_raw);
    
    while (matcher.find()) {
        eventCount++
            
        // create calendar event
        iCalMap.event.put(eventCount.toString(),[:])
        
        // populate calendar event
        iCalMap.event[eventCount.toString()].put("end", matcher.group(2))
        iCalMap.event[eventCount.toString()].put("start", matcher.group(3))
        iCalMap.event[eventCount.toString()].put("description", matcher.group(5))
    }
    
    //log.debug "${iCalMap}"
    
    // Sort data based on calendar event start
    if (eventCount) {
        iCalMap.event = iCalMap.event.values()sort{ a, b -> a.start <=> b.start}
    }
    
    return [iCalMap, eventCount]
}

def iCalProcessEventDateTime(data) {
    //log.debug "timeHelp data= $data"
    Date zDate
    
    if (data.contains("Z")) zDate =  toDateTime(data)
    else if (data.contains("T")) zDate = new SimpleDateFormat("yyyyMMdd'T'kkmmss").parse(data)
    else zDate = new SimpleDateFormat("yyyyMMdd").parse(data)
        
    //log.debug "zDate= $zDate"
    String eventTime = new SimpleDateFormat("HH:mm").format(zDate)
    String eventDate = new SimpleDateFormat("dd-MM-yy").format(zDate)
    
    //log.debug "timeHelp return=$eventTime & $eventDate & $zDate"     
    return [eventTime, eventDate, zDate]
}

def iCalProcessTodayEvent(icallink) {
    HashMap iCalMap = [:] 
    Integer eCount = 0
    iCalMap.put("event",[:])
    
    deviceScheduledEvent = [:]
    
    //log.debug "iCalProcessTodayEvent() - icallink: ${icallink}"
    
    String today = new SimpleDateFormat("yyyyMMdd").format(new Date())
    
    try {
        uri = icallink
        if(uri.startsWith(" ")) uri = uri.replaceFirst(" ","")
        Map reqParams = [
            uri: uri,
            timeout: 10
        ]

        httpGet(reqParams) { resp ->
            if(resp.status == 200) {

                log.debug "rest status ${resp.status}"
                wkStr = resp.data
                (iCalMap, eventCount) = iCalParse(wkStr.text)
                
                if (eventCount) {
                    //log.debug "iCalProcessTodayEvent() - found ${eventCount} calendar events"
                    // find if today
                    todayCalendarEvent = iCalMap.event.find { iCalProcessEventDateTime(it.start) == iCalProcessEventDateTime(today) }
                    
                    if (todayCalendarEvent) {
                        (scheduleStartTime, _, scheduleStartDate) = iCalProcessEventDateTime(todayCalendarEvent.start)
                        (scheduleEndTime, _, scheduleEndDate) = iCalProcessEventDateTime(todayCalendarEvent.end)
                        
                        //log.debug "iCalProcessTodayEvent() - Event Start Offset (minutes): ${settings.offsetStart}"
                        //log.debug "iCalProcessTodayEvent() - Event End Offset (minutes): ${settings.offsetEnd}"
                        
                        // Apply calendar event start offset, if any
                        if (settings.offsetStart) {
                            int minutesOffset = settings.offsetStart
                            use (TimeCategory) {
                                scheduleStartDate += minutesOffset.minutes
                            }
                        }
                        
                        // Apply calendar event end offset, if any
                        if (settings.offsetEnd) {
                            int minutesOffset = settings.offsetEnd
                            use (TimeCategory) {
                                scheduleEndDate += minutesOffset.minutes
                            }
                        }
                        
                        deviceScheduledEvent.put("start", scheduleStartDate)
                        deviceScheduledEvent.put("end", scheduleEndDate)
                        deviceScheduledEvent.put("description", todayCalendarEvent.description)
                        
                    }
                }

            } //end 200 resp
            else { // not 200
                log.warn "${device} Response code ${resp.status}"
            }
        } //end http get
        
    } //end try
    catch (e) {
        log.warn "${device} CATCH $e"
    }
    
    return deviceScheduledEvent
}

String CalendarRenderViewFromICS(icslink) {
    //theCal = "<div style='height:100%;width:100%'><iframe src='${gCal}' style='height:100%;width:100%;border:none'></iframe></div>"
    def iframe = """<iframe id="open-web-calendar" 
        style="background:url('https://raw.githubusercontent.com/niccokunzmann/open-web-calendar/master/static/img/loaders/circular-loader.gif') center center no-repeat;"
        src="https://open-web-calendar.herokuapp.com/calendar.html?url=${icslink}"
        sandbox="allow-scripts allow-same-origin allow-top-navigation"
        allowTransparency="true" scrolling="no" 
        frameborder="0" height="600px" width="100%">
    </iframe>""".stripIndent()
    
    return "${iframe}"
}
