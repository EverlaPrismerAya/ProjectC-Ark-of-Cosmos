package net.everla.projectc.arkofcosmos.combat;

/** Scoped to Rin's native death routine, never a spatial or global particle filter. */
public final class SatsukiDeathParticles {
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    public static boolean active(){return ACTIVE.get();}
    public static void run(Runnable action){
        boolean previous=ACTIVE.get();ACTIVE.set(true);
        try{action.run();}finally{if(previous)ACTIVE.set(true);else ACTIVE.remove();}
    }
}
