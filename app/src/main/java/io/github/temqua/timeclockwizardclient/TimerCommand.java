package io.github.temqua.timeclockwizardclient;

public enum TimerCommand {

    ClockIn("Clock in"),
    ClockOut("Clock out");
    private final String command;

    TimerCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
