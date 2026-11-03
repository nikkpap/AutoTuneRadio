import gr.radiorise.player.ReminderTiming;
import gr.radiorise.player.LogRedactor;
import java.util.*;
import java.time.*;

public class ReminderTimingCheck {
 static int checks=0;
 static void check(boolean condition,String label) {checks++;if(!condition) throw new AssertionError(label);}
 static ReminderTiming.Alarm alarm(String key,int minutes,long start,long end) {
  return new ReminderTiming.Alarm(key,key+"|"+minutes,ReminderTiming.trigger(start,minutes),end);
 }
 public static void main(String[] args) {
  long now=1_800_000_000_000L,start=now+60*60_000L,end=start+30*60_000L;
  String key=ReminderTiming.occurrence(1,7,start);
  List<ReminderTiming.Alarm> pair=List.of(alarm(key,5,start,end),alarm(key,30,start,end));
  List<ReminderTiming.Alarm> planned=ReminderTiming.plan(pair,now,Set.of(),Set.of());
  check(planned.size()==2,"30+5 coexist");
  check(planned.get(0).at==start-30*60_000L,"30min first");
  check(planned.get(1).at==start-5*60_000L,"5min second");
  check(ReminderTiming.trigger(start,0)==start,"start-time reminder");
  check(ReminderTiming.plan(pair,now,Set.of(key+"|30"),Set.of()).size()==1,"delivered dedup");
  check(ReminderTiming.plan(pair,now,Set.of(),Set.of(key)).isEmpty(),"Cancel all occurrence thresholds");
  String repeat=ReminderTiming.occurrence(1,7,start+86400000L);
  check(!key.equals(repeat),"recurring occurrences independent");
  check(!key.equals(ReminderTiming.occurrence(2,7,start)),"calendar identity independent");
  check(ReminderTiming.plan(List.of(pair.get(0),pair.get(0)),now,Set.of(),Set.of()).size()==1,"duplicate candidates");
  check(ReminderTiming.eligible(now-4*60_000L,now,end),"short catchup allowed");
  check(!ReminderTiming.eligible(now-6*60_000L,now,end),"stale reminder rejected");
  check(!ReminderTiming.eligible(now,now,now),"ended occurrence rejected");
  long snooze=now+10*60_000L;
  var s=new ReminderTiming.Alarm(key,key+"|s"+snooze,snooze,end);
  check(ReminderTiming.plan(List.of(s),now,Set.of(),Set.of()).get(0).at==snooze,"snooze new deadline");
  check(ReminderTiming.plan(List.of(s),now,Set.of(s.token),Set.of()).isEmpty(),"snooze delivered dedup");
  List<ReminderTiming.Alarm> many=new ArrayList<>();
  for(int i=200;i>=0;i--) many.add(new ReminderTiming.Alarm("e"+i,"e"+i+"|0",now+i*1000L,end));
  var bounded=ReminderTiming.plan(many,now,Set.of(),Set.of());
  check(bounded.size()==128,"alarm bound");
  check(bounded.get(0).at==now && bounded.get(127).at==now+127000,"nearest alarms retained");
  check(ReminderTiming.plan(List.of(new ReminderTiming.Alarm("far","far|0",now+49*3600000L,now+50*3600000L)),now,Set.of(),Set.of()).isEmpty(),"48h horizon");
  for(String time:List.of("2026-03-29T04:00:00+03:00","2026-10-25T03:30:00+02:00")) {
   long utc=OffsetDateTime.parse(time).toInstant().toEpochMilli();
   check(utc-ReminderTiming.trigger(utc,30)==1800000L,"DST UTC timing "+time);
  }
  boolean rejected=false;try{ReminderTiming.trigger(start,-1);}catch(IllegalArgumentException e){rejected=true;}
  check(rejected,"invalid offset rejected");
  check(!LogRedactor.sanitize("error HTTPS://user:password@radio/live?token=secret next").contains("secret"),"URI credentials removed");
  check(LogRedactor.sanitize("content://provider/image/1 file:///photos/a.jpg mailto:test@example.com").equals("[URI] [URI] [URI]"),"content/file/mail URIs removed");
  check(LogRedactor.sanitize("EQ session=3\nstate=ready").equals("EQ session=3\nstate=ready"),"diagnostic lines preserved");
  check(LogRedactor.sanitize("x".repeat(20000)).length()==12000,"log line bound");
  System.out.println("PASS: "+checks+" reminder and log checks");
 }
}
