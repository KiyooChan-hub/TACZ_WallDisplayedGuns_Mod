package dev.kiyo.wallgun.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionCacheTest {
    @Test void retainsMoreThanOldCountAndVertexLimits(){
        var disposed=new ArrayList<Long>();
        var cache=new SessionCache<Integer,Long>(Long::longValue,disposed::add);
        for(int i=0;i<5000;i++)cache.put(i,100_000L);
        assertEquals(5000,cache.size());assertEquals(500_000_000L,cache.weight());
        for(int i=0;i<5000;i++)assertEquals(100_000L,cache.get(i));
        assertTrue(disposed.isEmpty());
    }
    @Test void identicalAppearanceSharesValueAcrossDestinations(){
        var cache=new SessionCache<String,Object>(v->1,v->{});
        Object mesh=new Object();cache.put(new String("gun+attachments"),mesh);
        for(int destination=0;destination<100;destination++)assertSame(mesh,cache.get(new String("gun+attachments")));
        assertEquals(1,cache.size());
    }
    @Test void differentAppearanceDoesNotReuseOldMesh(){
        var cache=new SessionCache<String,Object>(v->1,v->{});
        var original=new Object();var modified=new Object();
        cache.put("original",original);cache.put("changed",modified);
        assertSame(original,cache.get("original"));assertSame(modified,cache.get("changed"));
    }
    @Test void explicitInvalidationAndDisconnectReleaseExactlyOnce(){
        var disposed=new ArrayList<Integer>();var cache=new SessionCache<String,Integer>(Integer::longValue,disposed::add);
        cache.put("a",4);cache.put("b",6);cache.remove("a");
        assertEquals(List.of(4),disposed);assertEquals(6,cache.weight());
        cache.clear();cache.clear();assertEquals(List.of(4,6),disposed);
        assertEquals(0,cache.weight());assertEquals(0,cache.size());assertNull(cache.get("b"));
    }
    @Test void replacementUpdatesAccounting(){
        var disposed=new ArrayList<Integer>();var cache=new SessionCache<String,Integer>(Integer::longValue,disposed::add);
        cache.put("a",4);cache.put("a",8);
        assertEquals(List.of(4),disposed);assertEquals(8,cache.weight());assertEquals(1,cache.size());
    }
}
