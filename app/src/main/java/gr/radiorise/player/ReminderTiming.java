package gr.radiorise.player;

/** UTC milliseconds keep reminders correct across timezone and DST changes. */
public final class ReminderTiming {
    private ReminderTiming() {}
    public static final class Alarm {
        public final String occurrence, token;
        public final long at, end;
        public Alarm(String occurrence, String token, long at, long end) {
            this.occurrence=occurrence; this.token=token; this.at=at; this.end=end;
        }
    }
    /** Bound alarms to nearest 48 hours and 128 entries; periodic refresh replenishes. */
    public static java.util.List<Alarm> plan(java.util.List<Alarm> candidates, long now,
            java.util.Set<String> delivered, java.util.Set<String> dismissed) {
        java.util.Map<String,Alarm> unique=new java.util.HashMap<>();
        for(Alarm a:candidates) {
            if(!delivered.contains(a.token) && !dismissed.contains(a.occurrence)
                    && eligible(a.at,now,a.end) && a.at <= now+48*60*60*1000L)
                unique.put(a.token,a);
        }
        java.util.List<Alarm> result=new java.util.ArrayList<>(unique.values());
        result.sort(java.util.Comparator.comparingLong((Alarm a)->a.at).thenComparing(a->a.token));
        return new java.util.ArrayList<>(result.subList(0,Math.min(128,result.size())));
    }
    public static long trigger(long begin, int minutes) {
        if (minutes < 0 || minutes > 1440) throw new IllegalArgumentException("minutes");
        return begin - minutes * 60_000L;
    }
    public static boolean eligible(long trigger, long now, long end) {
        return end > now && trigger >= now - 5 * 60_000L;
    }
    public static String occurrence(long calendarId, long eventId, long begin) {
        return calendarId + ":" + eventId + ":" + begin;
    }
}
