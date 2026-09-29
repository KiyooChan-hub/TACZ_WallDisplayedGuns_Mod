package dev.kiyo.wallgun.client;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WeightedCacheTest {
    @Test void leastRecentlyUsedIsDisposed() {
        var disposed=new ArrayList<Integer>();var cache=new WeightedCache<String,Integer>(10,Integer::longValue,disposed::add);
        cache.put("a",4);cache.put("b",4);cache.get("a");cache.put("c",4);
        assertNull(cache.get("b"));assertEquals(4,cache.get("a"));assertEquals(List.of(4),disposed);assertEquals(8,cache.weight());
    }
    @Test void activeSceneDoesNotThrashWhenOverBudget() {
        var cache=new WeightedCache<String,Integer>(3,Integer::longValue,v->{});
        cache.pins(Set.of("a","b"));cache.put("a",4);cache.put("b",4);
        assertEquals(8,cache.weight());cache.pins(Set.of("b"));assertNull(cache.get("a"));assertEquals(4,cache.get("b"));
        cache.pins(Set.of());assertEquals(0,cache.weight());
    }
    @Test void newDemandModelCanBePinnedBeforeAdmission() {
        var cache=new WeightedCache<String,Integer>(3,Integer::longValue,v->{});cache.pin("a");cache.put("a",4);
        assertEquals(4,cache.get("a"));cache.pins(Set.of());assertNull(cache.get("a"));
    }
    @Test void clearDisposesAllAndResetsPins() {
        var disposed=new ArrayList<Integer>();var cache=new WeightedCache<String,Integer>(10,Integer::longValue,disposed::add);
        cache.pin("a");cache.put("a",4);cache.clear();assertEquals(List.of(4),disposed);assertEquals(0,cache.size());assertEquals(0,cache.weight());
    }
    @Test void legacyClientOnlyBlockingIsNotAConfigChoice() throws Exception {
        assertEquals(List.of("LOADING","OFF"),Arrays.stream(dev.kiyo.wallgun.WallGunConfig.PreloadMode.values()).map(Enum::name).toList());
        try(var input=getClass().getResourceAsStream("/tacz_wall_display.mixins.json")) {
            assertNotNull(input);assertFalse(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("TerrainWarmupMixin"));
        }
    }
}
