package com.example.baget.orderproduct;

public final class WorkshopRules {
    private WorkshopRules() {}
    public enum State { WAITING, ACCEPTED, IN_PROGRESS, PAUSED, COMPLETED, STOPPED }
    public static boolean editable(State state) { return state == State.ACCEPTED || state == State.IN_PROGRESS; }
    public static boolean transition(State from, State to) {
        return switch (from) {
            case WAITING -> to == State.ACCEPTED;
            case ACCEPTED -> to == State.IN_PROGRESS || to == State.PAUSED;
            case IN_PROGRESS -> to == State.PAUSED;
            case PAUSED -> to == State.IN_PROGRESS;
            default -> false;
        };
    }
    public static boolean needsReason(State from, State to) { return from == State.PAUSED || to == State.PAUSED; }
}
