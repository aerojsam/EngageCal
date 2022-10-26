library (
        author: "aerojsam",
        category: "Utils",
        description: "iCalendar Helpers",
        name: "EngageCal.CalendarResources",
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

/* Based on VEVENT Format Definition:
 * https://icalendar.org/iCalendar-RFC-5545/3-6-1-event-component.html
 */
@Field static String ICAL_SPLIT_REGEX="""\\s(?=(?:DTSTAMP|UID|DTSTART|CLASS|CREATED|DESCRIPTION|GEO|LAST-MOD|LOCATION|ORGANIZER|PRIORITY|SEQ|STATUS|SUMMARY|TRANSP
                                        |URL|RECURID|RRULE|DTEND|DURATION|ATTACH|ATTENDEE|CATEGORIES|COMMENT|CONTACT|EXDATE|RSTATUS|RELATED|RESOURCES|RDATE|X-PROP|IANA_PROP)\\S*:\\S+)""".stripMargin()

/*
 * Inspired by https://github.com/Mark-C-uk/Hubitat/blob/master/ical
 */
def iCalParse(ical_raw) {
    HashMap iCalMap = [:]
    Integer eventCount = 0, idx = 0
    String ical_processed
    
    iCalMap.put("event",[:])
    
    ical_processed = ical_raw.replace("\n", " ").replace("\r", " ");
    ical_processed_split = ical_raw.split(ICAL_SPLIT_REGEX)
    
    log.debug "RAW>>"
    log.debug "${ical_processed}"
    log.debug "RAW<<"
    
    log.debug "PX>>"
    log.debug "${ical_processed_split}"
    log.debug "PX<<"

    for (String element : ical_processed_split) {
        
        // Start of Calendar Event (BEGIN:VEVENT)
        if ( element.trim().contains("BEGIN") && element.trim().contains("VEVENT") ) {
            eventCount = eventCount + 1
            log.debug "New Calendar Event (${eventCount})"
            iCalMap.event.put(eventCount.toString(),[:])
        }

        // Ahead of the BEGIN:VEVENT, make sure token is not empty
        if ( eventCount != 0 ) {
            // if such token is DSTART, we've found the calendar event start date
            if ( element.trim().contains("DTSTART") )
                iCalMap.event[eventCount.toString()].put("start", element.split(":")[1].trim())
            else if ( element.trim().contains("DTEND") )
                iCalMap.event[eventCount.toString()].put("end", element.split(":")[1].trim())
            else if ( element.trim().contains("DESCRIPTION") ) {
                iCalMap.event[eventCount.toString()].put("description", element.split(":", limit=2)[1].trim())
            }
        }
        
        idx++
    }

    log.debug "${iCalMap}"
    
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

def iCalProcessTodayEvent(icallink, eventOffsetStartSeconds, eventOffsetEndSeconds) {
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
                        if (eventOffsetStartSeconds) {
                            int secondsOffset = eventOffsetStartSeconds
                            use (TimeCategory) {
                                scheduleStartDate += secondsOffset.seconds
                                log.debug "iCalProcessTodayEvent() - Schedule Start Date ${scheduleStartDate} (offset ${secondsOffset.seconds})"
                            }
                        }
                        
                        // Apply calendar event end offset, if any
                        if (eventOffsetEndSeconds) {
                            int secondsOffset = eventOffsetEndSeconds
                            use (TimeCategory) {
                                scheduleEndDate += secondsOffset.seconds
                                log.debug "iCalProcessTodayEvent() - Schedule End Date ${scheduleEndDate} (offset ${secondsOffset.seconds})"
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
