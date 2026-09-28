package dev.kiyo.wallgun.smoke;
/** The warmup smoke entry now exercises the non-blocking replacement. */
final class WarmupSmoke { WarmupSmoke() {new NonBlockingSmoke();} }
