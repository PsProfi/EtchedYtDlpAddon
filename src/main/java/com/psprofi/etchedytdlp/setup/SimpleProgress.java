package com.psprofi.etchedytdlp.setup;

public class SimpleProgress {
    private final String title;
    private final int totalSteps;
    private int currentStep = 0;

    public SimpleProgress(String title, int totalSteps) {
        this.title = title;
        this.totalSteps = totalSteps;
        log("Starting...");
    }

    public void step(String message) {
        currentStep++;
        log("(" + currentStep + "/" + totalSteps + ") " + message);
    }

    public void fail(String message) {
        log("[FAILED] " + message);
    }

    public void finish(String message) {
        log("[DONE] " + message);
    }

    private void log(String msg) {
        System.out.println("[" + title + "] " + msg);
    }
}
