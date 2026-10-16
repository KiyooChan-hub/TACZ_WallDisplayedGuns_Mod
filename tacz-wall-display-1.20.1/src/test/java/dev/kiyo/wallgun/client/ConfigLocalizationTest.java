package dev.kiyo.wallgun.client;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
class ConfigLocalizationTest {
    @Test void everyConfigHasLocalizedNameAndTooltip()throws Exception{
        String source=Files.readString(Path.of("src/main/java/dev/kiyo/wallgun/WallGunConfig.java"));
        var matches=Pattern.compile("\\.translation\\(\"([^\"]+)\"\\)").matcher(source);
        var zh=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tacz_wall_display/lang/zh_cn.json"))).getAsJsonObject();
        var en=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tacz_wall_display/lang/en_us.json"))).getAsJsonObject();
        int count=0;
        while(matches.find()){
            count++;
            for(var suffix:new String[]{"",".tooltip"}){
                String key=matches.group(1)+suffix;
                assertTrue(zh.has(key),key);assertTrue(en.has(key),key);
                assertTrue(zh.get(key).getAsString().matches("(?s).*[\\p{IsHan}].*"),key);
            }
        }
        assertEquals(3,count);
        assertTrue(source.contains(".define(\"cacheDecorativeGuns\",true)"));
    }
    @Test void runtimePolicyIsWiredToAllCacheLayers()throws Exception{
        String source=Files.readString(Path.of("src/main/java/dev/kiyo/wallgun/client/RenderQueue.java"));
        assertTrue(source.contains("CACHE_POLICY.release(WallGunConfig.cacheDecorativeGuns(),current!=world)"));
        for(String call:new String[]{"WallBatches.clear()","GunMeshes.clear()","CreateMovingGuns.clear()"})assertTrue(source.contains(call),call);
    }
}
