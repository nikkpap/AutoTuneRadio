package gr.radiorise.player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;

/** Bounded, offline iCalendar reader. Unsupported recurrence forms fail explicitly. */
public final class IcsCalendar {
    public static final class Occurrence {
        public final long eventId, begin, end;
        public final String title, location;
        Occurrence(Event e, ZonedDateTime start) {
            eventId=id(e.uid);begin=start.toInstant().toEpochMilli();
            end=start.plus(e.duration).toInstant().toEpochMilli();title=e.title;location=e.location;
        }
    }
    private static final class Property {
        String name,value; Map<String,String> params=new HashMap<>();
    }
    private static final class Event {
        String uid,title,location;ZonedDateTime start,recurrence;Duration duration;
        int sequence;boolean allDay,canceled;Map<String,String> rule=new HashMap<>();
        Set<Long> excluded=new HashSet<>();List<ZonedDateTime> added=new ArrayList<>();
    }
    private final List<Event> events;
    public final int eventCount;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT);
    private static final List<String> DAYS=Arrays.asList("MO","TU","WE","TH","FR","SA","SU");
    private IcsCalendar(List<Event> events) {this.events=events;eventCount=events.size();}
    public static IcsCalendar parse(String input,ZoneId localZone) {
        if(input.length()>2_000_000) throw bad(Tr.t("Το ICS ξεπερνά τα 2 MB.","The ICS is larger than 2 MB."));
        List<String> lines=new ArrayList<>();
        for(String line:input.replace("\uFEFF","").split("\\r?\\n|\\r",-1)) {
            if((line.startsWith(" ")||line.startsWith("\t"))&&!lines.isEmpty()) lines.set(lines.size()-1,lines.get(lines.size()-1)+line.substring(1));
            else lines.add(line);
        }
        List<Event> events=new ArrayList<>();List<Property> current=null;
        int nested=0;boolean opened=false,closed=false;ZoneId zone=localZone;
        for(String line:lines) {
            if(line.equalsIgnoreCase("BEGIN:VCALENDAR")) {if(opened)throw bad(Tr.t("Πολλαπλά VCALENDAR δεν υποστηρίζονται.","Multiple VCALENDARs are not supported."));opened=true;continue;}
            if(line.equalsIgnoreCase("END:VCALENDAR")) {closed=true;continue;}
            if(line.equalsIgnoreCase("BEGIN:VEVENT")) {if(!opened||closed)throw bad(Tr.t("VEVENT εκτός VCALENDAR.","VEVENT outside VCALENDAR."));if(current!=null)throw bad(Tr.t("Μη έγκυρη δομή VEVENT.","Invalid VEVENT structure."));current=new ArrayList<>();nested=0;continue;}
            if(line.equalsIgnoreCase("END:VEVENT")) {
                if(current==null||nested!=0)throw bad(Tr.t("Μη έγκυρο VEVENT.","Invalid VEVENT."));events.add(event(current,zone));current=null;
                if(events.size()>5000)throw bad(Tr.t("Μέγιστο 5.000 συμβάντα ανά ICS.","Maximum 5,000 events per ICS."));continue;
            }
            if(current!=null) {
                if(line.regionMatches(true,0,"BEGIN:",0,6)) {nested++;continue;}
                if(line.regionMatches(true,0,"END:",0,4)) {if(nested<=0)throw bad(Tr.t("Μη έγκυρο END στο VEVENT.","Invalid END inside VEVENT."));nested--;continue;}
                if(nested==0 && !line.isEmpty()) current.add(property(line));
            } else if(line.regionMatches(true,0,"X-WR-TIMEZONE:",0,14)) zone=zone(line.substring(14));
        }
        if(!opened||!closed||current!=null)throw bad(Tr.t("Δεν είναι πλήρες αρχείο iCalendar (.ics).","Not a complete iCalendar (.ics) file."));
        // Prefer higher SEQUENCE; last occurrence wins equal-sequence duplicates.
        Map<String,Event> unique=new LinkedHashMap<>();
        for(Event e:events) {String key=e.uid+"|"+(e.recurrence==null?"master":e.recurrence.toInstant().toEpochMilli());Event old=unique.get(key);if(old==null||e.sequence>=old.sequence)unique.put(key,e);}
        return new IcsCalendar(new ArrayList<>(unique.values()));
    }
    private static Property property(String line) {
        boolean quoted=false;int colon=-1;
        for(int i=0;i<line.length();i++) {char c=line.charAt(i);if(c=='"')quoted=!quoted;if(c==':'&&!quoted){colon=i;break;}}
        if(colon<0)throw bad(Tr.t("Μη έγκυρη γραμμή ICS.","Invalid ICS line."));
        String[] head=line.substring(0,colon).split(";");Property p=new Property();p.name=head[0].toUpperCase(Locale.ROOT);p.value=line.substring(colon+1);
        for(int i=1;i<head.length;i++) {String[] pair=head[i].split("=",2);if(pair.length==2)p.params.put(pair[0].toUpperCase(Locale.ROOT),pair[1].replace("\"",""));}
        return p;
    }
    private static Property first(List<Property> props,String name) {for(Property p:props)if(p.name.equals(name))return p;return null;}
    private static String value(List<Property> props,String name,String fallback) {Property p=first(props,name);return p==null?fallback:p.value;}
    private static Event event(List<Property> props,ZoneId zone) {
        Event e=new Event();Property start=first(props,"DTSTART"),rid=first(props,"RECURRENCE-ID");
        e.sequence=Integer.parseInt(value(props,"SEQUENCE","0"));
        e.canceled=value(props,"STATUS","").equalsIgnoreCase("CANCELLED");
        if(start==null && e.canceled)start=rid;
        if(start==null)throw bad(Tr.t("Λείπει DTSTART από συμβάν.","An event is missing DTSTART."));
        e.allDay="DATE".equals(start.params.get("VALUE"))||start.value.length()==8;
        e.start=date(start,start.value,zone);
        e.title=text(value(props,"SUMMARY",Tr.t("Συνάντηση","Meeting")));e.location=text(value(props,"LOCATION",""));
        e.uid=value(props,"UID",e.start.toString()+"|"+e.title);
        if(rid!=null) {if(rid.params.containsKey("RANGE"))throw bad(Tr.t("RECURRENCE-ID RANGE δεν υποστηρίζεται.","RECURRENCE-ID RANGE is not supported."));e.recurrence=date(rid,rid.value,zone);}
        Property end=first(props,"DTEND"),duration=first(props,"DURATION");
        if(end!=null && duration!=null)throw bad(Tr.t("Συμβάν με DTEND και DURATION μαζί.","Event with both DTEND and DURATION."));
        if(end!=null)e.duration=Duration.between(e.start,date(end,end.value,zone));
        else if(duration!=null) {
            if(duration.value.matches("P[0-9]+W"))e.duration=Duration.ofDays(Long.parseLong(duration.value.substring(1,duration.value.length()-1))*7);
            else e.duration=Duration.parse(duration.value);
            if(!e.allDay&&duration.value.matches(".*[DW].*"))throw bad(Tr.t("Ονομαστική διάρκεια ημερών/εβδομάδων δεν υποστηρίζεται. Χρησιμοποίησε DTEND.","Nominal day/week durations are not supported. Use DTEND."));
        } else e.duration=e.allDay?Duration.ofDays(1):Duration.ZERO;
        if(e.duration.isNegative())throw bad(Tr.t("Το DTEND προηγείται του DTSTART.","DTEND is before DTSTART."));
        if(props.stream().filter(p->p.name.equals("RRULE")).count()>1)throw bad(Tr.t("Πολλαπλά RRULE στο ίδιο συμβάν.","Multiple RRULEs in the same event."));
        Property rule=first(props,"RRULE");
        if(rule!=null) {
            for(String part:rule.value.toUpperCase(Locale.ROOT).split(";")) {String[] pair=part.split("=",2);if(pair.length!=2)throw bad(Tr.t("Μη έγκυρο RRULE.","Invalid RRULE."));e.rule.put(pair[0],pair[1]);}
            Set<String> supported=new HashSet<>(Arrays.asList("FREQ","INTERVAL","COUNT","UNTIL","BYDAY","BYMONTHDAY","BYMONTH","WKST"));
            for(String key:e.rule.keySet())if(!supported.contains(key))throw bad(Tr.t("Δεν υποστηρίζεται RRULE "+key+". Εξήγαγε μεμονωμένες συναντήσεις.","RRULE "+key+" is not supported. Export individual meetings."));
            if(!Arrays.asList("DAILY","WEEKLY","MONTHLY","YEARLY").contains(e.rule.get("FREQ")))throw bad(Tr.t("Μη υποστηριζόμενη συχνότητα RRULE.","Unsupported RRULE frequency."));
            positive(e.rule.getOrDefault("INTERVAL","1"));if(e.rule.containsKey("COUNT"))positive(e.rule.get("COUNT"));
            if(e.rule.containsKey("COUNT")&&e.rule.containsKey("UNTIL"))throw bad(Tr.t("RRULE με COUNT και UNTIL μαζί.","RRULE with both COUNT and UNTIL."));
            if(e.rule.containsKey("UNTIL"))date(new Property(),e.rule.get("UNTIL"),e.start.getZone());
            if(e.rule.containsKey("WKST")&&!DAYS.contains(e.rule.get("WKST")))throw bad(Tr.t("Μη έγκυρο WKST.","Invalid WKST."));
            if(e.rule.containsKey("BYMONTH"))integers(e.rule.get("BYMONTH"),1,12,false);
            if(e.rule.containsKey("BYMONTHDAY"))integers(e.rule.get("BYMONTHDAY"),-31,31,true);
            if(e.rule.get("FREQ").equals("WEEKLY")&&e.rule.containsKey("BYMONTHDAY"))throw bad(Tr.t("WEEKLY/BYMONTHDAY δεν υποστηρίζεται.","WEEKLY/BYMONTHDAY is not supported."));
            if(e.rule.get("FREQ").equals("YEARLY")&&(e.rule.containsKey("BYDAY")||e.rule.containsKey("BYMONTHDAY")))throw bad(Tr.t("YEARLY με BYDAY/BYMONTHDAY δεν υποστηρίζεται. Εξήγαγε μεμονωμένα συμβάντα.","YEARLY with BYDAY/BYMONTHDAY is not supported. Export individual events."));
            if(e.rule.containsKey("BYDAY"))for(String d:e.rule.get("BYDAY").split(",")) {
                if(!d.matches("[+-]?[1-5]?(MO|TU|WE|TH|FR|SA|SU)"))throw bad(Tr.t("Μη έγκυρο BYDAY.","Invalid BYDAY."));
                if(d.length()>2&&!e.rule.get("FREQ").equals("MONTHLY"))throw bad(Tr.t("Αριθμητικό BYDAY υποστηρίζεται μόνο σε MONTHLY.","Numbered BYDAY is supported only with MONTHLY."));
            }
        }
        for(Property p:props) {
            if(p.name.equals("EXRULE"))throw bad(Tr.t("EXRULE δεν υποστηρίζεται.","EXRULE is not supported."));
            if(p.name.equals("RDATE")||p.name.equals("EXDATE"))for(String v:p.value.split(",")) {
                if(v.contains("/"))throw bad(Tr.t("RDATE PERIOD δεν υποστηρίζεται.","RDATE PERIOD is not supported."));
                ZonedDateTime d=date(p,v,zone);if(p.name.equals("EXDATE"))e.excluded.add(d.toInstant().toEpochMilli());else e.added.add(d);
            }
        }
        return e;
    }
    public List<Occurrence> expand(long from,long to) {
        if(to<=from)throw bad(Tr.t("Μη έγκυρο χρονικό διάστημα.","Invalid time range."));
        List<Occurrence> out=new ArrayList<>();int[] budget={2_000_000};
        Set<String> overrides=new HashSet<>();
        for(Event e:events)if(e.recurrence!=null)overrides.add(e.uid+"|"+e.recurrence.toInstant().toEpochMilli());
        for(Event e:events) {
            if(e.canceled||e.allDay)continue;
            Set<ZonedDateTime> dates=new TreeSet<>();dates.add(e.start);dates.addAll(e.added);
            if(!e.rule.isEmpty()&&e.recurrence==null) {
                int count=e.rule.containsKey("COUNT")?positive(e.rule.get("COUNT")):Integer.MAX_VALUE,seen=0;
                long until=e.rule.containsKey("UNTIL")?date(new Property(),e.rule.get("UNTIL"),e.start.getZone()).toInstant().toEpochMilli():Long.MAX_VALUE;
                LocalDate d=e.start.toLocalDate();
                if(!e.rule.containsKey("COUNT")) {LocalDate near=Instant.ofEpochMilli(from).atZone(e.start.getZone()).toLocalDate().minusDays(Math.min(366,e.duration.toDays()+1));if(near.isAfter(d))d=near;}
                LocalDate last=Instant.ofEpochMilli(Math.min(to,until)).atZone(e.start.getZone()).toLocalDate();
                for(;!d.isAfter(last);d=d.plusDays(1)) {
                    if(--budget[0]<0)throw bad(Tr.t("Το ICS έχει υπερβολικά πολλές επαναλήψεις.","The ICS has too many repetitions."));
                    if(!matches(e,d))continue;
                    LocalDateTime local=LocalDateTime.of(d,e.start.toLocalTime());
                    if(e.start.getZone().getRules().getValidOffsets(local).isEmpty())continue;
                    ZonedDateTime instance=local.atZone(e.start.getZone());
                    if(instance.isBefore(e.start)||instance.toInstant().toEpochMilli()>until)continue;
                    if(++seen>count)break;dates.add(instance);
                }
            }
            for(ZonedDateTime start:dates) {
                long at=start.toInstant().toEpochMilli();
                if(e.excluded.contains(at)||(e.recurrence==null&&overrides.contains(e.uid+"|"+at)))continue;
                Occurrence o=new Occurrence(e,start);
                if(o.begin<to && Math.max(o.end,o.begin+1000)>from)out.add(o);
                if(out.size()>10000)throw bad(Tr.t("Πάρα πολλές συναντήσεις στο χρονικό διάστημα.","Too many meetings in the time range."));
            }
        }
        out.sort(Comparator.comparingLong(o->o.begin));return out;
    }
    private static boolean matches(Event e,LocalDate d) {
        LocalDate base=e.start.toLocalDate();Map<String,String> r=e.rule;String freq=r.get("FREQ");int interval=positive(r.getOrDefault("INTERVAL","1"));
        long delta;
        switch(freq) {
            case "DAILY":delta=ChronoUnit.DAYS.between(base,d);break;
            case "WEEKLY":DayOfWeek week=DayOfWeek.of(DAYS.indexOf(r.getOrDefault("WKST","MO"))+1);delta=ChronoUnit.WEEKS.between(base.with(TemporalAdjusters.previousOrSame(week)),d.with(TemporalAdjusters.previousOrSame(week)));break;
            case "MONTHLY":delta=ChronoUnit.MONTHS.between(YearMonth.from(base),YearMonth.from(d));break;
            default:delta=d.getYear()-base.getYear();
        }
        if(delta<0||delta%interval!=0)return false;
        if(r.containsKey("BYMONTH")&&!integers(r.get("BYMONTH"),1,12,false).contains(d.getMonthValue()))return false;
        if(r.containsKey("BYMONTHDAY")) {Set<Integer> days=integers(r.get("BYMONTHDAY"),-31,31,true);if(!days.contains(d.getDayOfMonth())&&!days.contains(d.getDayOfMonth()-d.lengthOfMonth()-1))return false;}
        if(r.containsKey("BYDAY")) {
            boolean match=false;for(String rule:r.get("BYDAY").split(",")) {
                String day=rule.substring(rule.length()-2);if(DAYS.indexOf(day)+1!=d.getDayOfWeek().getValue())continue;
                if(rule.length()==2)match=true;else {int n=Integer.parseInt(rule.substring(0,rule.length()-2));if(n>0&&(d.getDayOfMonth()-1)/7+1==n || n<0&&(d.lengthOfMonth()-d.getDayOfMonth())/7+1==-n)match=true;}
            }if(!match)return false;
        }
        if(freq.equals("WEEKLY")&&!r.containsKey("BYDAY")&&d.getDayOfWeek()!=base.getDayOfWeek())return false;
        if((freq.equals("MONTHLY")||freq.equals("YEARLY"))&&!r.containsKey("BYDAY")&&!r.containsKey("BYMONTHDAY")&&d.getDayOfMonth()!=base.getDayOfMonth())return false;
        if(freq.equals("YEARLY")&&!r.containsKey("BYMONTH")&&d.getMonth()!=base.getMonth()) {
            if(!r.containsKey("BYDAY")&&!r.containsKey("BYMONTHDAY"))return false;
        }
        return true;
    }
    private static Set<Integer> integers(String value,int min,int max,boolean noZero) {Set<Integer> out=new HashSet<>();for(String s:value.split(",")){int n=Integer.parseInt(s);if(n<min||n>max||noZero&&n==0)throw bad(Tr.t("Μη έγκυρος αριθμός RRULE.","Invalid RRULE number."));out.add(n);}return out;}
    private static int positive(String value) {int n=Integer.parseInt(value);if(n<1||n>1_000_000)throw bad(Tr.t("Μη έγκυρο INTERVAL/COUNT.","Invalid INTERVAL/COUNT."));return n;}
    private static ZoneId zone(String value) {try{return ZoneId.of(value);}catch(DateTimeException ex){throw bad(Tr.t("Μη υποστηριζόμενη ζώνη ώρας: "+value+". Εξήγαγε σε UTC ή Europe/Athens.","Unsupported time zone: "+value+". Export in UTC or Europe/Athens."));}}
    private static ZonedDateTime date(Property p,String value,ZoneId fallback) {
        ZoneId z=p.params.containsKey("TZID")?zone(p.params.get("TZID")):fallback;
        if(value.length()==8)return LocalDate.parse(value,DATE).atStartOfDay(z);
        if(value.endsWith("Z"))return LocalDateTime.parse(value.substring(0,value.length()-1),TIME).atZone(ZoneOffset.UTC);
        return LocalDateTime.parse(value,TIME).atZone(z);
    }
    private static String text(String value) {StringBuilder b=new StringBuilder();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='\\'&&i+1<value.length()){c=value.charAt(++i);if(c=='n'||c=='N')c='\n';}b.append(c);}return b.toString();}
    private static long id(String value) {try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));long id=0;for(int i=0;i<8;i++)id=(id<<8)|(b[i]&255L);return id&Long.MAX_VALUE;}catch(Exception e){throw new IllegalStateException(e);}}
    private static IllegalArgumentException bad(String message) {return new IllegalArgumentException(message);}
}
