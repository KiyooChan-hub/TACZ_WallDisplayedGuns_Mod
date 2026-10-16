package dev.kiyo.wallgun.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CachePolicyTest {
    @Test void enabledRetainsAcrossAnyWorldChange(){
        var policy=new CachePolicy();
        for(int i=0;i<100;i++){assertFalse(policy.release(true,true));assertFalse(policy.release(true,false));}
    }
    @Test void disablingReleasesOnceNotEveryFrame(){
        var policy=new CachePolicy();
        assertTrue(policy.release(false,false));
        for(int i=0;i<100;i++)assertFalse(policy.release(false,false));
        assertTrue(policy.release(false,true));assertFalse(policy.release(false,false));
    }
    @Test void reEnablingPreservesCurrentSceneAndCanDisableAgain(){
        var policy=new CachePolicy();
        assertTrue(policy.release(false,true));assertFalse(policy.release(true,false));
        assertFalse(policy.release(true,true));assertTrue(policy.release(false,false));
    }
}
