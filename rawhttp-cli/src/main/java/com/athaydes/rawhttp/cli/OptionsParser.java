package com.athaydes.rawhttp.cli;

import java.io.File;
import java.util.Optional;

final class ServerOptions {
    static final int DEFAULT_SERVER_PORT = 8080;

    final File dir;
    final int port;
    final boolean logRequests;

    public ServerOptions(File dir, int port, boolean logRequests) {
        this.dir = dir;
        this.port = port;
        this.logRequests = logRequests;
    }
}

final class OptionsException extends Exception {
    OptionsException(String message) {
        super(message);
    }
}

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
final class Options {
    final Optional<File> requestFile;
    final boolean showHelp;
    final Optional<ServerOptions> serverOptions;

    Options(Optional<File> requestFile, boolean showHelp, Optional<ServerOptions> serverOptions) {
        this.requestFile = requestFile;
        this.showHelp = showHelp;
        this.serverOptions = serverOptions;
    }
}

final class OptionsParser {

    static Options parse(String[] args) throws OptionsException {
        boolean help = showHelp(args);
        Optional<File> requestFile = getRequestFile(args);
        Optional<ServerOptions> serverOptions = getServerOptions(args);

        for (String arg : args) {
            if (!arg.isEmpty()) {
                throw new OptionsException("Unrecognized option: " + arg);
            }
        }

        return new Options(requestFile, help, serverOptions);
    }

    private static boolean showHelp(String[] args) {
        int index = 0;
        boolean result = false;
        while (index < args.length) {
            switch (args[index]) {
                case "--help":
                case "-h":
                    args[index] = "";
                    result = true;
            }
            index++;
        }
        return result;
    }

    private static Optional<File> getRequestFile(String[] args)
            throws OptionsException {
        File requestFile = null;
        int index = 0;
        while (index < args.length) {
            switch (args[index]) {
                case "--file":
                case "-f":
                    if (requestFile != null) {
                        throw new OptionsException("--file -f: option cannot be used more than once");
                    }
                    args[index] = "";
                    index++;
                    if (index < args.length) {
                        requestFile = new File(args[index]);
                        args[index] = "";
                    } else {
                        throw new OptionsException("--file -f: option requires a file argument");
                    }
            }
            index++;
        }
        return Optional.ofNullable(requestFile);
    }

    private static Optional<ServerOptions> getServerOptions(String[] args)
            throws OptionsException {
        boolean logRequests = false;
        int port = ServerOptions.DEFAULT_SERVER_PORT;
        File dir = new File(".").getAbsoluteFile();
        boolean serverOptionProvided = false;

        int index = 0;
        while (index < args.length) {
            switch (args[index]) {
                case "--log-requests":
                case "-l":
                    logRequests = true;
                    args[index] = "";
                    break;
                case "--server":
                case "-s":
                    if (serverOptionProvided) {
                        throw new OptionsException("--server -s: option cannot be used more than once");
                    }
                    serverOptionProvided = true;
                    args[index] = "";
                    index++;
                    if (index < args.length) {
                        dir = new File(args[index]);
                        args[index] = "";
                        index++;
                        if (index < args.length) {
                            try {
                                port = Integer.valueOf(args[index]);
                                args[index] = "";
                            } catch (NumberFormatException e) {
                                index--; // not an int, so move back as maybe this is another option
                            }
                        }
                    } else {
                        index--; // move back as dir is not mandatory
                    }
            }
            index++;
        }

        if (serverOptionProvided) {
            return Optional.of(new ServerOptions(dir, port, logRequests));
        } else {
            return Optional.empty();
        }
    }

}