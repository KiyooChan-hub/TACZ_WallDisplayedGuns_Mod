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
    private int phase,activeTicks,waitTicks,checks,observerTicks;
    private boolean lastWaiting;
    private final StringBuilder results=new StringBuilder();
    public ActivationSmoke(){
        if(FMLEnvironment.dist==Dist.CLIENT){ClientAcceptance.init();return;}
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
        event.getServer().overworld().setDefaultSpawnPos(new BlockPos(0,100,8),180);
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event){
        var p=(ServerPlayer)event.getEntity();p.teleportTo(p.server.overworld(),0.5,100,8.5,180,0);p.setHealth(20);
    }
    private void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(!scenario.equals("standard") && !scenario.equals("performance"))try {
            if(activeTicks!=0 || (scenario.equals("timeout") && waitTicks<2200))throw new AssertionError("failure path activated player or timed out early");
            Files.writeString(Path.of("SUCCESS-"+scenario+".txt"),"PASS "+scenario+" disconnected without activation; protected ticks="+waitTicks);
            ((ServerPlayer)event.getEntity()).server.halt(false);
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private void tick(ServerTickEvent.Post event){
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
