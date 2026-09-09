package com.ofss.digx.cz.bea.app.logger;

import com.ofss.fc.infra.thread.ThreadContext;

public class BeaSystemOut {

    public static void println(String message) {
        try {
            final StringBuilder messageBuffer = new StringBuilder("[< ").append(ThreadContext.getThreadInstance().getTraceReferenceNumber()).append(" >] ").append("\t").append(message);
            System.out.println(messageBuffer.toString());
        } catch (Exception ep) {
            ep.printStackTrace();
            System.out.println(message);
        }
    }

    public static void println(Object message) {
        try {
            final StringBuilder messageBuffer = new StringBuilder("[< ").append(ThreadContext.getThreadInstance().getTraceReferenceNumber()).append(" >] ").append("\t").append(message);
            System.out.println(messageBuffer.toString());
        } catch (Exception ep) {
            ep.printStackTrace();
            System.out.println(message);
        }
    }

    public static void printErr(Exception e) {
        try {
            final StringBuilder messageBuffer = new StringBuilder("[< ").append(ThreadContext.getThreadInstance().getTraceReferenceNumber()).append(" >] ").append("\t").append(e.getMessage());
            System.err.println(messageBuffer.toString());
            e.printStackTrace();
        } catch (Exception ep) {
            ep.printStackTrace();
            e.printStackTrace();
        }
    }

    public static void printErr(Throwable e) {
        try {
            final StringBuilder messageBuffer = new StringBuilder("[< ").append(ThreadContext.getThreadInstance().getTraceReferenceNumber()).append(" >] ").append("\t").append(e.getMessage());
            System.err.println(messageBuffer.toString());
            e.printStackTrace();
        } catch (Exception ep) {
            ep.printStackTrace();
            e.printStackTrace();
        }
    }

    public static void printErr(String message, Exception e) {
        try {
            final StringBuilder messageBuffer = new StringBuilder("[< ").append(ThreadContext.getThreadInstance().getTraceReferenceNumber()).append(" >] ").append("\t").append(message);
            System.err.println(messageBuffer.toString());
            e.printStackTrace();
        } catch (Exception ep) {
            ep.printStackTrace();
            System.err.println(message);
            e.printStackTrace();
        }
    }
}