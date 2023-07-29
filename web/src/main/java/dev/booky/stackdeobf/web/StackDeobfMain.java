package dev.booky.stackdeobf.web;
// Created by booky10 in StackDeobfuscator (16:34 06.07.23)

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.io.IoBuilder;

public final class StackDeobfMain {

    private static final String HTTP_BIND = System.getProperty("web.bind", "localhost");
    private static final int HTTP_PORT = Integer.getInteger("web.port", 8082);

    static {
        System.setProperty("java.awt.headless", "true");
        System.setOut(IoBuilder.forLogger("STDOUT").setLevel(Level.INFO).buildPrintStream());
        System.setErr(IoBuilder.forLogger("STDERR").setLevel(Level.ERROR).buildPrintStream());
    }

    private StackDeobfMain() {
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        Thread.currentThread().setName("Startup Thread");

        StackDeobfService service = new StackDeobfService(HTTP_BIND, HTTP_PORT);
        service.start(startTime);
    }
}
