package net.everla.projectc.arkofcosmos.entity;

/** Rolling damage-call window; an active orbit has a fixed duration. */
final class DistantOrbitTrigger {
    private final java.util.ArrayDeque<Long> hits=new java.util.ArrayDeque<>();
    private long until;
    boolean active(long now){return now<until;}
    void hit(long now){
        if(active(now))return;
        while(!hits.isEmpty()&&now-hits.peekFirst()>=60)hits.removeFirst();
        hits.addLast(now);
        if(hits.size()>=59){until=now+36*20;hits.clear();}
    }
}
