package dev.javatofde.starter.model;

/** Local eval fault injection. Never accept this field in a real deployment. */
public enum DemoFault {
    NONE,
    TIMEOUT,
    MALFORMED,
    OVERAPPROVE
}
