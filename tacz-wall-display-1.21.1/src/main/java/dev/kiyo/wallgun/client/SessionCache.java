package dev.kiyo.wallgun.client;
import java.util.*;
import java.util.function.*;

/** Explicit lifetime, no capacity or idle eviction. Keys/values must not retain a world or entity. */
public final class SessionCache<K,V> {
    private final Map<K,V> values=new HashMap<>();
    private final ToLongFunction<V> weight;
    private final Consumer<V> dispose;
    private long size;
    public SessionCache(ToLongFunction<V> weight,Consumer<V> dispose){this.weight=weight;this.dispose=dispose;}
    public V get(K key){return values.get(key);}
    public void remove(K key){V old=values.remove(key);if(old!=null){size-=weight.applyAsLong(old);dispose.accept(old);}}
    public void put(K key,V value){
        V old=values.put(key,value);
        if(old!=null){size-=weight.applyAsLong(old);if(old!=value)dispose.accept(old);}
        size+=weight.applyAsLong(value);
    }
    public long weight(){return size;}
    public int size(){return values.size();}
    public void clear(){values.values().forEach(dispose);values.clear();size=0;}
}
