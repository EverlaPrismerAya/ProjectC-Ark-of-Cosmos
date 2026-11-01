package net.everla.projectc.arkofcosmos.entity;

/** Detect blocked movement independently of whether AI counters are still advancing. */
final class DistantOrbitTrigger {
    private long until,lastTick=Long.MIN_VALUE;
    private int stalled;
    boolean active(long now){return now<until;}
    void observe(long now,boolean eligible,boolean blocked){
        if(now==lastTick)return;
        if(now-lastTick!=1)stalled=0;
        lastTick=now;
        if(active(now)||!eligible||!blocked){stalled=0;return;}
        if(++stalled>=20){until=now+36*20;stalled=0;}
    }
}
