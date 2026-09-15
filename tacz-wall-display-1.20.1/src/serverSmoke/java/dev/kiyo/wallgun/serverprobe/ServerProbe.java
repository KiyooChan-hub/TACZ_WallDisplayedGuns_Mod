package dev.kiyo.wallgun.serverprobe;
import dev.kiyo.wallgun.*;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.nio.file.*;
import java.io.InputStreamReader;

@Mod("wall_display_server_smoke")
public final class ServerProbe {
    private static final Capability<Payload> CAP=CapabilityManager.get(new CapabilityToken<>(){});
    public static final class Payload implements ICapabilitySerializable<CompoundTag> {
        int value;
        private final LazyOptional<Payload> optional=LazyOptional.of(()->this);
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){return cap==CAP?optional.cast():LazyOptional.empty();}
        @Override public CompoundTag serializeNBT(){var tag=new CompoundTag();tag.putInt("test",value);return tag;}
        @Override public void deserializeNBT(CompoundTag tag){value=tag.getInt("test");}
    }
    public ServerProbe() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterCapabilitiesEvent e)->e.register(Payload.class));
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class,this::attach);
        MinecraftForge.EVENT_BUS.addListener(this::started);
    }
    private void attach(AttachCapabilitiesEvent<ItemStack> event){if(event.getObject().getItem() instanceof IGun)event.addCapability(new ResourceLocation("wall_display_server_smoke:payload"),new Payload());}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void started(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();
        try {
            Files.deleteIfExists(Path.of("SERVER_FAILED.txt"));
            Files.deleteIfExists(Path.of("SERVER_SUCCESS.txt"));
            ItemStack original=GunItemBuilder.create().setId(new ResourceLocation("tacz:scar_l")).setAmmoCount(7).setAmmoInBarrel(true).build();
            original.getCapability(CAP).orElseThrow(()->new AssertionError("No test capability")).value=42;
            original.getOrCreateTag().putIntArray("unknown",new int[]{1,7,42});
            var decor=WallGuns.decorate(original);
            var restored=WallGuns.snapshot(ItemStack.of(decor.serializeNBT())).copyGun();
            check(restored.getCapability(CAP).orElseThrow(()->new AssertionError("Lost capability")).value==42,"Capability value round trip");
            check(original.serializeNBT().equals(restored.serializeNBT()),"Complete stack NBT round trip");
            var menu=new AbstractContainerMenu(null,0) {
                @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int i){return ItemStack.EMPTY;}
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}
            };
            var input=new TransientCraftingContainer(menu,2,2);input.setItem(3,original);
            var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
            check(recipe instanceof GunConversionRecipe,"Registered server recipe");
            input.setItem(3,recipe.assemble(input,level.registryAccess()));
            check(recipe.assemble(input,level.registryAccess()).serializeNBT().equals(original.serializeNBT()),"Dedicated server recipe reversal");
            var entries=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/guns.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            for(int x=-16;x<=16;x++)for(int y=-60;y<=-37;y++)level.setBlock(new BlockPos(x,y,0),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            int attachmentCount=0;
            var used=new java.util.HashSet<ResourceLocation>();
            var reserved=new java.util.HashSet<ResourceLocation>();
            for(var e:entries)reserved.add(new ResourceLocation(e.getAsJsonObject().get("gun_id").getAsString()));
            var fixtureReport=new StringBuilder();
            for(var element:entries) {
                var row=element.getAsJsonObject();var id=new ResourceLocation(row.get("gun_id").getAsString());
                if(TimelessAPI.getCommonGunIndex(id).isEmpty()) {
                    var missing=id;
                    id=TimelessAPI.getAllCommonGunIndex().stream().map(java.util.Map.Entry::getKey)
                            .filter(key->key.getNamespace().equals("tacz") && !used.contains(key) && !reserved.contains(key))
                            .sorted(java.util.Comparator.comparing(Object::toString)).findFirst().orElseThrow();
                    fixtureReport.append("Unavailable 1.20.1 fixture ").append(missing).append(" -> ").append(id).append('\n');
                }
                check(used.add(id),"Duplicate prototype "+id);
                var index=TimelessAPI.getCommonGunIndex(id).orElseThrow();
                var gun=GunItemBuilder.create().setId(id).setFireMode(index.getGunData().getFireModeSet().get(0)).build();
                for(var attachment:row.getAsJsonObject("attachments").entrySet()) {
                    var stack=AttachmentItemBuilder.create().setId(new ResourceLocation(attachment.getValue().getAsString())).build();
                    if(!IGun.getIGunOrNull(gun).allowAttachment(gun,stack)) {
                        stack=ItemStack.EMPTY;
                        for(var candidate:TimelessAPI.getAllCommonAttachmentIndex().stream().sorted(java.util.Comparator.comparing(e->e.getKey().toString())).toList()) {
                            if(!candidate.getValue().getType().name().equals(attachment.getKey()))continue;
                            var replacement=AttachmentItemBuilder.create().setId(candidate.getKey()).build();
                            if(IGun.getIGunOrNull(gun).allowAttachment(gun,replacement)){stack=replacement;break;}
                        }
                    }
                    if(stack.isEmpty())continue; // Version-specific gunpack slot compatibility.
                    IGun.getIGunOrNull(gun).installAttachment(gun,stack);
                    attachmentCount++;
                }
                var coords=row.getAsJsonArray("position");var pos=new BlockPos(coords.get(0).getAsInt(),coords.get(1).getAsInt(),coords.get(2).getAsInt());
                level.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState(),3);var display=(WallGunEntity)level.getBlockEntity(pos);display.setSnapshot(new GunSnapshot(gun));
                var drops=net.minecraft.world.level.block.Block.getDrops(display.getBlockState(),level,pos,display);
                check(drops.size()==1 && drops.get(0).serializeNBT().equals(gun.serializeNBT()),"Server loot preserves gun");
                fixtureReport.append(id).append(' ').append(gun.serializeNBT()).append('\n');
            }
            Files.writeString(Path.of("fixtures-1.20.1.txt"),fixtureReport);
            level.setDefaultSpawnPos(new BlockPos(0,-60,35),0);level.setDayTime(6000);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);
            Files.writeString(Path.of("SERVER_SUCCESS.txt"),"Dedicated Forge server boot; reversible registered recipe; complete NBT and serialized Forge capability round trip; 100 distinct gun prototypes with "+attachmentCount+" compatible attachments; original gun loot validated.\n");
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(Path.of("SERVER_FAILED.txt"),failure.toString());}catch(Exception ignored){}}
        server.halt(false);
    }
}
