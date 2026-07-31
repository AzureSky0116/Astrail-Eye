package dev.astrail.eye.api.service;

/** Marks live module state for the next coalesced configuration save. */
@FunctionalInterface
public interface ConfigurationService {
    void markDirty();
}
