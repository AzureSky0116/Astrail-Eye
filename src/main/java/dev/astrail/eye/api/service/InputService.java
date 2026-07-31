package dev.astrail.eye.api.service;

public interface InputService {
    InputLease acquire(String owner, InputAction action);

    boolean isPressed(InputAction action);

    void clear();
}
