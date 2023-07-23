package io.github.temqua.timeclockwizardclient;

public enum TimerCommand {

    ClockIn("Clock In"),
    ClockOut("Clock Out");
    private final String command;

    TimerCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
