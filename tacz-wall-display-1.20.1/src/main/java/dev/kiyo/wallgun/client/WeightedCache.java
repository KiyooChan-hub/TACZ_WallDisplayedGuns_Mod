package dev.kiyo.wallgun.client;

import java.util.*;
import java.util.function.*;

/** Render-thread LRU. Active models may exceed the retention budget; never thrash the current scene. */
public final class WeightedCache<K,V> {
    private final LinkedHashMap<K,V> values=new LinkedHashMap<>(16,.75f,true);
    private final ToLongFunction<V> weight;
    private final Consumer<V> dispose;
    private Set<K> pinned=Set.of();
    private long limit, size;
    public WeightedCache(long limit,ToLongFunction<V> weight,Consumer<V> dispose) {this.limit=limit;this.weight=weight;this.dispose=dispose;}
    public V get(K key) {return values.get(key);}
    public void put(K key,V value) {V old=values.put(key,value);if(old!=null){size-=weight.applyAsLong(old);if(old!=value)dispose.accept(old);}size+=weight.applyAsLong(value);trim();}
    public void pin(K key) {var keys=new HashSet<>(pinned);keys.add(key);pinned=Set.copyOf(keys);}
    public void pins(Set<K> keys) {pinned=Set.copyOf(keys);trim();}
    public void limit(long value) {limit=value;trim();}
    private void trim() {
        var iterator=values.entrySet().iterator();
        while((size>limit || values.size()>4096) && iterator.hasNext()) {var entry=iterator.next();if(pinned.contains(entry.getKey()))continue;size-=weight.applyAsLong(entry.getValue());dispose.accept(entry.getValue());iterator.remove();}
    }
    public long weight() {return size;}
    public int size() {return values.size();}
    public void clear() {values.values().forEach(dispose);values.clear();pinned=Set.of();size=0;}
}
