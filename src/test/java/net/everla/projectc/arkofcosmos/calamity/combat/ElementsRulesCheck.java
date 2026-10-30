package net.everla.projectc.arkofcosmos.calamity.combat;

/** Independent source checkpoints, including strict release-window endpoints. */
public final class ElementsRulesCheck {
    public static void main(String[] args){
        int[] expected={0,1,0,1,2};for(int i=0;i<5;i++)require(ElementsRules.mode(i)==expected[i],"Five-part sequence");
        require(!ElementsRules.snapWindow(11)&&ElementsRules.snapWindow(12)&&ElementsRules.snapWindow(59)&&!ElementsRules.snapWindow(60),"15%-75% snap window");
        close(ElementsRules.throwRatio(0),0);close(ElementsRules.throwRatio(.25),1);close(ElementsRules.throwRatio(.5),1);close(ElementsRules.throwRatio(.75),1);close(ElementsRules.throwRatio(1),0);
        close(ElementsRules.BASE_DAMAGE,5);close(ElementsRules.charged(ElementsRules.BASE_DAMAGE,0),5);close(ElementsRules.charged(ElementsRules.BASE_DAMAGE,10),6.75);require(ElementsRules.afterAttack(1)==0&&ElementsRules.afterAttack(0)==0,"Charge consumption");
        close(ElementsRules.blastDamage(10,0),33.75);close(ElementsRules.blastDamage(10,10),8.4375);
        require(ElementsRules.stitches(32)==0&&ElementsRules.stitches(45)==8&&ElementsRules.stitches(70)==8,"Eight stitches");
        int pulses=0;for(int i=1;i<=20;i++)if(ElementsRules.dotDue(i))pulses++;require(pulses==4,"20-tick resonance has four pulses");
        pulses=0;for(int i=1;i<=30;i++)if(ElementsRules.dotDue(i))pulses++;require(pulses==6,"30-tick resonance has six pulses");
        close(ElementsRules.MAX_REACH,31.25);require(ElementsRules.PARRY_WINDOW==15&&ElementsRules.PARRY_COOLDOWN==340,"Source parry timing");
        System.out.println("Elements source checkpoints passed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void close(double actual,double expected){if(Math.abs(actual-expected)>.00001)throw new AssertionError(actual+" != "+expected);}
}
