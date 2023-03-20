package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (18:03 20.03.23)

class RemappedThrowable extends Throwable {

    private final String className;

    public RemappedThrowable(String message, Throwable cause, String className) {
        super(message, cause);
        this.className = className;
    }

    public String getClassName() {
        return this.className;
    }

    @Override
    public String toString() {
        String message = this.getLocalizedMessage();
        if (message == null) {
            return this.className;
        }
        return this.className + ": " + message;
    }
}
