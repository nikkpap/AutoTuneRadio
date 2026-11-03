import gr.radiorise.player.IcsCalendar;
import java.time.*;
import java.util.*;

public class IcsCalendarCheck {
 static int checks=0;
 static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
 static long at(String date){return Instant.parse(date).toEpochMilli();}
 static String calendar(String events){return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"+events+"END:VCALENDAR\r\n";}
 static String event(String properties){return "BEGIN:VEVENT\r\n"+properties+"END:VEVENT\r\n";}
 static List<IcsCalendar.Occurrence> read(String text,String from,String to){return IcsCalendar.parse(text,ZoneId.of("Europe/Athens")).expand(at(from),at(to));}
 static void rejects(String properties,String label){boolean failed=false;try{IcsCalendar.parse(calendar(event(properties)),ZoneId.of("UTC"));}catch(Exception e){failed=true;}check(failed,label);}
 public static void main(String[] args){
  String single=calendar(event("UID:a\r\nDTSTART:20261012T120000Z\r\nDTEND:20261012T130000Z\r\nSUMMARY:Meet\\, team\\nRoom\r\n 2\r\nLOCATION:Office\\; A\r\nBEGIN:VALARM\r\nTRIGGER:-PT5M\r\nSUMMARY:Ignore alarm title\r\nEND:VALARM\r\n"));
  var a=read(single,"2026-10-10T00:00:00Z","2026-10-17T00:00:00Z");
  check(a.size()==1,"single event");check(a.get(0).begin==at("2026-10-12T12:00:00Z"),"UTC time");
  check(a.get(0).title.equals("Meet, team\nRoom2"),"fold and escape text");check(a.get(0).location.equals("Office; A"),"location escape");
  check(a.get(0).eventId==read(single,"2026-10-10T00:00:00Z","2026-10-17T00:00:00Z").get(0).eventId,"stable UID identity");
  String daily=calendar(event("UID:b\r\nDTSTART;TZID=Europe/Athens:20261024T100000\r\nDTEND;TZID=Europe/Athens:20261024T110000\r\nRRULE:FREQ=DAILY;COUNT=3\r\nEXDATE;TZID=Europe/Athens:20261025T100000\r\nRDATE;TZID=Europe/Athens:20261027T100000\r\n"));
  var b=read(daily,"2026-10-23T00:00:00Z","2026-10-29T00:00:00Z");
  check(b.size()==3,"COUNT plus EXDATE plus RDATE");check(b.get(0).begin==at("2026-10-24T07:00:00Z"),"DST before");check(b.get(1).begin==at("2026-10-26T08:00:00Z"),"DST after preserves local hour");
  String weekly=calendar(event("UID:w\r\nDTSTART:20261012T100000Z\r\nDURATION:PT1H\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=4\r\n"));
  check(read(weekly,"2026-10-10T00:00:00Z","2026-10-30T00:00:00Z").size()==4,"weekly BYDAY COUNT");
  String monthly=calendar(event("UID:m\r\nDTSTART:20260131T100000Z\r\nDURATION:PT1H\r\nRRULE:FREQ=MONTHLY;COUNT=3\r\n"));
  var m=read(monthly,"2026-01-01T00:00:00Z","2026-06-01T00:00:00Z");check(m.size()==3&&m.get(1).begin==at("2026-03-31T10:00:00Z"),"invalid month dates skipped");
  String override=calendar(event("UID:x\r\nDTSTART:20261012T100000Z\r\nDTEND:20261012T110000Z\r\nRRULE:FREQ=DAILY;COUNT=3\r\n")+event("UID:x\r\nRECURRENCE-ID:20261013T100000Z\r\nDTSTART:20261013T120000Z\r\nDTEND:20261013T130000Z\r\n")+event("UID:x\r\nRECURRENCE-ID:20261014T100000Z\r\nSTATUS:CANCELLED\r\n"));
  var o=read(override,"2026-10-10T00:00:00Z","2026-10-17T00:00:00Z");check(o.size()==2&&o.get(1).begin==at("2026-10-13T12:00:00Z"),"moved and canceled instances");
  String allDay=calendar(event("UID:d\r\nDTSTART;VALUE=DATE:20261012\r\nDTEND;VALUE=DATE:20261013\r\n"));check(read(allDay,"2026-10-10T00:00:00Z","2026-10-17T00:00:00Z").isEmpty(),"all-day excluded consistently with device calendar");
  String floating=calendar(event("UID:f\r\nDTSTART:20261012T100000\r\nDTEND:20261012T110000\r\n"));check(read(floating,"2026-10-10T00:00:00Z","2026-10-17T00:00:00Z").get(0).begin==at("2026-10-12T07:00:00Z"),"floating uses device zone");
  rejects("DTSTART;TZID=Custom/MadeUp:20261012T100000\r\n","unknown timezone rejected");
  rejects("DTSTART:20261012T100000Z\r\nRRULE:FREQ=MONTHLY;BYSETPOS=1\r\n","unsupported rule rejected");
  rejects("DTSTART:20261012T100000Z\r\nRRULE:FREQ=DAILY;INTERVAL=0\r\n","invalid interval rejected");
  rejects("DTSTART:20260230T100000Z\r\n","invalid date rejected");
  boolean invalid=false;try{IcsCalendar.parse("not ICS",ZoneId.of("UTC"));}catch(Exception e){invalid=true;}check(invalid,"not calendar rejected");
  String until=calendar(event("UID:u\r\nDTSTART:20261012T100000Z\r\nDTEND:20261012T110000Z\r\nRRULE:FREQ=DAILY;UNTIL=20261013T100000Z\r\n"));check(read(until,"2026-10-10T00:00:00Z","2026-10-17T00:00:00Z").size()==2,"UNTIL inclusive");
  String duplicate=calendar(event("UID:r\r\nSEQUENCE:2\r\nDTSTART:20261012T120000Z\r\nDTEND:20261012T130000Z\r\n")+event("UID:r\r\nSEQUENCE:1\r\nDTSTART:20261012T100000Z\r\nDTEND:20261012T110000Z\r\n"));check(read(duplicate,"2026-10-10T00:00:00Z","2026-10-17T00:00:00Z").get(0).begin==at("2026-10-12T12:00:00Z"),"highest SEQUENCE preferred");
  System.out.println("PASS: "+checks+" ICS checks");
 }
}
