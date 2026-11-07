package net.everla.projectc.arkofcosmos.client;
import net.everla.projectc.arkofcosmos.enchant.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Separate input, action button and scroll selector, matching the source panel. */
public final class WitchScreen extends AbstractContainerScreen<WitchMenu> {
    private int selected;
    private ItemStack previous=ItemStack.EMPTY;
    public WitchScreen(WitchMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=280;imageHeight=232;inventoryLabelX=59;inventoryLabelY=122;titleLabelX=17;titleLabelY=10;}
    private static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/gui/witch/"+name+".png");}
    private List<Curse> options(){return Curse.of(menu.item())!=null||menu.product()?List.of():Arrays.stream(Curse.values()).filter(c->c.accepts(menu.item())).toList();}
    private int optionCount(){return options().size()+menu.infusions().size();}
    private NecromanticInfusion infusion(){selection();int index=selected-options().size();var recipes=menu.infusions();return index>=0&&index<recipes.size()?recipes.get(index):null;}
    private Curse selection(){
        if(!ItemStack.matches(previous,menu.item())){selected=0;previous=menu.item().copy();}
        if(menu.product())return null;
        var current=Curse.of(menu.item());if(current!=null)return current;
        var list=options();int count=optionCount();if(count==0)return null;selected=Math.floorMod(selected,count);return selected<list.size()?list.get(selected):null;
    }
    private boolean selector(double x,double y){return x>=leftPos+83&&x<leftPos+229&&y>=topPos+35&&y<topPos+72;}
    private boolean action(double x,double y){return x>=leftPos+60&&x<leftPos+82&&y>=topPos+40&&y<topPos+67;}
    private void switchBy(int direction){selection();int count=optionCount();if(count>0)selected=Math.floorMod(selected+direction,count);}
    private int displayedCost(){return infusion()!=null?NecromanticInfusion.COST:minecraft.player!=null&&minecraft.player.getAbilities().instabuild?0:menu.cost(Curse.of(menu.item())!=null);}
    private void image(GuiGraphics g,String name,int x,int y,int w,int h,int tw,int th){g.blit(texture(name),leftPos+x,topPos+y,w,h,0,0,tw,th,tw,th);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        image(g,"calamitascursebackground",0,0,imageWidth,imageHeight,392,324);
        for(var slot:menu.slots)if(slot!=menu.slots.get(0)){
            g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF8B6654);
            g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xFF34272F);
        }
        image(g,"calamitascurseitemslot",21,35,35,36,48,50);
        var curse=selection();
        var recipe=infusion();
        image(g,action(mx,my)&&(curse!=null||recipe!=null)?"calamitascurseui_buttonhovered":"calamitascurseui_button",60,42,19,20,26,28);
        if(curse!=null){
            var name=Component.translatable(curse.key());
            float scale=Math.min(1,122f/Math.max(1,font.width(name)));
            g.pose().pushPose();g.pose().translate(leftPos+155,topPos+49,0);g.pose().scale(scale,scale,1);
            g.drawCenteredString(font,name,0,0,0xFFE9AF);g.pose().popPose();
            image(g,"curseicon_"+curse.icon.toLowerCase(Locale.ROOT),230,82,24,24,40,40);
        }
        if(recipe!=null){g.drawCenteredString(font,Component.translatable("menu.arkofcosmos.necromantic_infusion"),leftPos+155,topPos+49,0xFFE9AF);g.renderItem(recipe.result(),leftPos+233,topPos+86);g.drawString(font,recipe.result().getHoverName(),leftPos+30,topPos+83,0xFFE9AF);}
        if(Curse.of(menu.item())==null&&optionCount()>0){
            image(g,"calamitascurseui_arrowup",149,35,10,7,14,10);
            image(g,"calamitascurseui_arrowdown",149,65,10,8,14,12);
        }
        if(Curse.of(menu.item())!=null&&!menu.product())g.drawString(font,Component.translatable("menu.arkofcosmos.remove_curse"),leftPos+30,topPos+83,0xFFE9AF);
        if(curse!=null||recipe!=null)g.drawString(font,Component.translatable("menu.arkofcosmos.cost",displayedCost()),leftPos+30,topPos+106,0xFFFFFF);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,titleLabelX,titleLabelY,0xFFE9AF,false);
        g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xFFE9AF,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        var curse=selection();var recipe=infusion();if(recipe!=null&&(selector(mx,my)||action(mx,my)))g.renderTooltip(font,Component.translatable("menu.arkofcosmos.necromantic_infusion.description"),mx,my);
        else if(recipe!=null&&mx>=leftPos+230&&mx<leftPos+253&&my>=topPos+82&&my<topPos+108)g.renderTooltip(font,recipe.result(),mx,my);
        else if(curse!=null&&selector(mx,my)){
            var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();lines.add(Component.translatable(curse.key()).getVisualOrderText());
            lines.addAll(font.split(Component.translatable(curse.key()+".description"),230));g.renderTooltip(font,lines,mx,my);
        }else if(curse!=null&&action(mx,my))g.renderTooltip(font,Component.translatable(Curse.of(menu.item())==null?"menu.arkofcosmos.enchant_action":"menu.arkofcosmos.remove_curse"),mx,my);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){if(selector(x,y)&&delta!=0){switchBy(delta<0?1:-1);return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0){var curse=selection();var recipe=infusion();if(recipe!=null&&action(x,y)){WitchEnchanting.infuse(menu,recipe);return true;}if(curse!=null&&action(x,y)){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,Curse.of(menu.item())!=null?0:curse.ordinal()+1);return true;}
            if(selector(x,y)&&y<topPos+42){switchBy(-1);return true;}if(selector(x,y)&&y>=topPos+65){switchBy(1);return true;}}
        return super.mouseClicked(x,y,button);
    }
}
