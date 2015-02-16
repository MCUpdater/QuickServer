package org.mcupdater;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;

import java.io.File;

/**
 * Created by sbarbour on 2/13/15.
 */
public class QuickServer {

    public static void main(String[] args){
        OptionParser optParser = new OptionParser();
        optParser.allowsUnrecognizedOptions();
        ArgumentAcceptingOptionSpec<String> packSpec = optParser.accepts("ServerPack").withRequiredArg().ofType(String.class);
        ArgumentAcceptingOptionSpec<File> rootSpec = optParser.accepts("MCURoot").withRequiredArg().ofType(File.class);
    }
}
