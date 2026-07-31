package dev.astrail.eye.core.event;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override
    void close();
}
