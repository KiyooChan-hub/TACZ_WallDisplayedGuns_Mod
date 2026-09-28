package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;

@Mod("wall_activation_smoke")
public final class ActivationSmoke {
    private final String scenario=System.getProperty("wallgun.scenario","standard");
    private int phase,activeTicks,waitTicks,checks,observerTicks,stressTicks,stressAdded;
    private boolean lastWaiting;
    private final StringBuilder results=new StringBuilder();
    public ActivationSmoke(){
        if(FMLEnvironment.dist==Dist.CLIENT){if(scenario.equals("editing"))EditAcceptance.init();else ClientAcceptance.init();return;}
        NeoForge.EVENT_BUS.addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(this::login);
        NeoForge.EVENT_BUS.addListener(this::logout);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void setup(ServerStartedEvent event){
        java.util.List<String> fixtureIds=java.util.List.of("tacz:ak47");
        if(scenario.equals("performance"))try {fixtureIds=Files.readAllLines(Path.of("performance-guns.txt"));}catch(java.io.IOException ignored){}
        for(var key:java.util.List.of(Level.OVERWORLD,Level.NETHER)) {
            var level=event.getServer().getLevel(key);
            for(int x=-8;x<=8;x++)for(int z=-2;z<=12;z++)level.setBlock(new BlockPos(x,98,z),Blocks.STONE.defaultBlockState(),3);
            for(int x=-8;x<=8;x++)for(int z=-2;z<=12;z++)for(int y=99;y<=112;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            for(int x=-9;x<=9;x++)for(int y=98;y<=113;y++)for(int z=-3;z<=13;z++)if(x==-9 || x==9 || z==-3 || z==13 || y==113)level.setBlock(new BlockPos(x,y,z),Blocks.GLASS.defaultBlockState(),3);
            if(scenario.equals("performance") && key==Level.NETHER)continue;
            for(int row=0;row<10;row++)for(int col=0;col<10;col++) {
                var pos=new BlockPos(col-5,100+row,0);
                level.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,Direction.SOUTH),3);
                var id=ResourceLocation.parse(fixtureIds.get((row*10+col)%fixtureIds.size()));
                var data=com.tacz.guns.api.TimelessAPI.getCommonGunIndex(id).orElseThrow().getGunData();
                var stack=com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(id).setAmmoCount(data.getAmmoAmount())
                    .setFireMode(data.getFireModeSet().getFirst()).setAmmoInBarrel(true).build(level.registryAccess());
                stack.set(DataComponents.CUSTOM_NAME,Component.literal("Activation fixture "+row+":"+col));
                ((WallGunEntity)level.getBlockEntity(pos)).setSnapshot(new GunSnapshot(stack));
            }
        }
        var fixture=event.getServer().overworld();
        fixture.setDayTime(6000);
        fixture.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
        for(int x=-5;x<5;x++)for(int y=99;y<109;y++)fixture.setBlock(new BlockPos(x,y,-6),Blocks.AIR.defaultBlockState(),3);
        if(!scenario.equals("editing"))for(int x=-5;x<5;x++)for(int y=99;y<109;y++)
            fixture.setBlock(new BlockPos(x,y,-8),Blocks.AIR.defaultBlockState(),3);
        var gun=(WallGunEntity)fixture.getBlockEntity(new BlockPos(-5,100,0));
        var chunk=fixture.getChunkAt(gun.getBlockPos());
        var before=LoadingSessions.describe(chunk);
        if(before!=LoadingSessions.describe(chunk))throw new AssertionError("description cache miss");
        gun.setPose(gun.roll()+1,!gun.flipped());
        if(before.equals(LoadingSessions.describe(chunk)))throw new AssertionError("pose mutation missed");
        gun.setPose(gun.roll()-1,!gun.flipped());
        var after=LoadingSessions.describe(chunk);
        if(!before.equals(after))throw new AssertionError("pose restoration missed");
        fixture.removeBlock(gun.getBlockPos(),false);
        if(after.equals(LoadingSessions.describe(chunk)))throw new AssertionError("removal missed");
        fixture.setBlock(gun.getBlockPos(),gun.getBlockState(),3);
        ((WallGunEntity)fixture.getBlockEntity(gun.getBlockPos())).setSnapshot(gun.snapshot());
        if(!after.equals(LoadingSessions.describe(chunk)))throw new AssertionError("addition missed");
        if(scenario.equals("editing")){
            for(int x=-8;x<=8;x++)for(int y=99;y<=112;y++)for(int z=-2;z<=12;z++)fixture.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            for(int row=0;row<10;row++)for(int col=0;col<10;col++){
                var p=new BlockPos(col-5,99+row,-8);
                fixture.setBlock(p,WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,Direction.SOUTH),3);
                ((WallGunEntity)fixture.getBlockEntity(p)).setSnapshot(new GunSnapshot(equipped(fixture.registryAccess())));
            }
            for(int x=-2;x<=3;x++)for(int z=2;z<=5;z++)fixture.setBlock(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState(),3);
            for(int x=0;x<2;x++)for(int y=100;y<102;y++)for(int z=0;z<2;z++){
                var p=new BlockPos(x,y,z);if(p.equals(new BlockPos(0,101,1)))continue;
                fixture.setBlock(p,WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,Direction.SOUTH),3);
                ((WallGunEntity)fixture.getBlockEntity(p)).setSnapshot(new GunSnapshot(equipped(fixture.registryAccess())));
            }
        }
        event.getServer().overworld().setDefaultSpawnPos(new BlockPos(0,100,8),180);
    }
    private static net.minecraft.world.item.ItemStack equipped(net.minecraft.core.HolderLookup.Provider lookup){
        var gun=com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(ResourceLocation.parse("spearhead:hk416d_145"))
            .setAmmoCount(7).setAmmoInBarrel(true).setFireMode(com.tacz.guns.api.item.gun.FireMode.SEMI).build(lookup);
        var api=com.tacz.guns.api.item.IGun.getIGunOrNull(gun);
        for(var id:java.util.List.of("scope_acog_ta31","muzzle_silencer_knight_qd","grip_vertical_military","stock_ripstock","laser_peq15"))
            api.installAttachment(lookup,gun,com.tacz.guns.api.item.builder.AttachmentItemBuilder.create().setId(ResourceLocation.parse("tacz:"+id)).build());
        return gun;
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event){
        var p=(ServerPlayer)event.getEntity();p.teleportTo(p.server.overworld(),0.5,100,8.5,180,0);p.setHealth(20);p.setGameMode(GameType.SURVIVAL);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);if(scenario.equals("editing")){p.setGameMode(GameType.CREATIVE);var held=equipped(p.registryAccess());
            com.tacz.guns.api.item.IGun.getIGunOrNull(held).installAttachment(p.registryAccess(),held,
                com.tacz.guns.api.item.builder.AttachmentItemBuilder.create().setId(ResourceLocation.parse("tacz:scope_elcan_4x")).build());
            p.getInventory().setItem(0,held);p.getInventory().selected=0;p.teleportTo(p.server.overworld(),.5,100,4,180,0);}
    }
    private void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(scenario.equals("editing")){((ServerPlayer)event.getEntity()).server.halt(false);return;}
        if(!scenario.equals("standard") && !scenario.equals("performance"))try {
            if(activeTicks!=0 || (scenario.equals("timeout") && waitTicks<2200))throw new AssertionError("failure path activated player or timed out early");
            Files.writeString(Path.of("SUCCESS-"+scenario+".txt"),"PASS "+scenario+" disconnected without activation; protected ticks="+waitTicks);
            ((ServerPlayer)event.getEntity()).server.halt(false);
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private void tick(ServerTickEvent.Post event){
        if(scenario.equals("editing")){
            for(var player:event.getServer().getPlayerList().getPlayers())
                if(player.getInventory().selected==1 && stressAdded<100 && ++stressTicks%4==0){
                    int i=stressAdded++;var pos=new BlockPos(i%10-5,99+i/10,-6);
                    var level=event.getServer().overworld();
                    level.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,Direction.SOUTH),3);
                    ((WallGunEntity)level.getBlockEntity(pos)).setSnapshot(new GunSnapshot(equipped(level.registryAccess())));
                }
            return;
        }
        if(event.getServer().getPlayerList().getPlayers().isEmpty()){if(phase==5)event.getServer().halt(false);return;}
        var p=event.getServer().getPlayerList().getPlayers().stream().filter(x->x.getGameProfile().getName().equals("Dev")).findFirst().orElse(null);
        if(p==null)return;
        try {
            boolean waiting=LoadingSessions.waiting(p);
            if(waiting){
                for(var other:event.getServer().getPlayerList().getPlayers())if(other!=p && !LoadingSessions.waiting(other))observerTicks++;
                if(!lastWaiting){waitTicks=0;activeTicks=0;}waitTicks++;
                float health=p.getHealth();int food=p.getFoodData().getFoodLevel();
                if(p.hurt(p.damageSources().lava(),5) || p.hurt(p.damageSources().fall(),5) || p.hurt(p.damageSources().inWall(),5) || health!=p.getHealth())throw new AssertionError("waiting player took damage");
                if(p.isPushable() || p.canBeSeenAsEnemy())throw new AssertionError("waiting player target/collision");
                p.push(1,1,1);if(p.getDeltaMovement().lengthSqr()!=0)throw new AssertionError("waiting player pushed");
                p.getFoodData().setFoodLevel(food);checks++;
            } else {
                if(lastWaiting){if(!scenario.equals("performance") && waitTicks<40)throw new AssertionError("test did not exercise delayed handshake");results.append("PASS phase ").append(phase).append(" protected ticks=").append(waitTicks).append('\n');}
                if(++activeTicks==60) {
                    activeTicks=0;
                    if(phase==0){phase=1;p.teleportTo(event.getServer().getLevel(Level.NETHER),0.5,100,8.5,180,0);}
                    else if(phase==1){phase=2;p.teleportTo(event.getServer().overworld(),0.5,100,8.5,180,0);}
                    else if(phase==2){phase=3;p.setRespawnPosition(Level.NETHER,new BlockPos(0,100,8),180,true,false);p.setHealth(0);var next=event.getServer().getPlayerList().respawn(p,false,Entity.RemovalReason.KILLED);p.connection.player=next;}
                    else if(phase==3){phase=4;p.teleportTo(event.getServer().overworld(),0.5,100,8.5,180,0);p.teleportTo(event.getServer().getLevel(Level.NETHER),0.5,100,8.5,180,0);}
                    else if(phase==4){phase=5;Files.writeString(Path.of("SUCCESS.txt"),results+"PASS damage/target/push checks="+checks+"; login, dimensions, new respawn instance, consecutive transfers; other-player ticks during waits="+observerTicks+"\n");}
                }
            }
            lastWaiting=waiting;
        }catch(Throwable ex){try{Files.writeString(Path.of("FAILED.txt"),ex.toString());}catch(Exception ignored){}ex.printStackTrace();event.getServer().halt(false);}
    }
}
