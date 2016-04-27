package org.mcupdater;

import com.google.gson.Gson;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.lang3.StringUtils;
import org.mcupdater.downloadlib.DownloadQueue;
import org.mcupdater.downloadlib.Downloadable;
import org.mcupdater.downloadlib.TrackerListener;
import org.mcupdater.instance.Instance;
import org.mcupdater.model.*;
import org.mcupdater.mojang.Library;
import org.mcupdater.mojang.MinecraftVersion;
import org.mcupdater.settings.Profile;
import org.mcupdater.util.MCUpdater;
import org.mcupdater.util.ServerPackParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Created by sbarbour on 2/13/15.
 */
public class QuickServer extends MCUApp implements TrackerListener {

    private final static Gson gson = new Gson();
    private static ArrayList<GenericModule> modList;
    private static Path installPath;
    private static ServerList pack;
    private Deque<DownloadQueue> queues = new ArrayDeque<>();

    public static void main(String[] args){
        OptionParser optParser = new OptionParser();
        optParser.allowsUnrecognizedOptions();
        ArgumentAcceptingOptionSpec<String> packSpec = optParser.accepts("ServerPack").withRequiredArg().ofType(String.class).required();
        ArgumentAcceptingOptionSpec<File> pathSpec = optParser.accepts("path").withRequiredArg().ofType(File.class).required();
        ArgumentAcceptingOptionSpec<String> serverIdSpec = optParser.accepts("id").withRequiredArg().ofType(String.class).required();
        optParser.accepts("clean","Clear existing install");
        final OptionSet options = optParser.parse(args);
        installPath = options.valueOf(pathSpec).toPath();
        MCUpdater.getInstance(installPath.toFile());
        MCUpdater.getInstance().setParent(new QuickServer());
        pack = ServerPackParser.loadFromURL(options.valueOf(packSpec), options.valueOf(serverIdSpec));
        modList = new ArrayList<>();
        List<ConfigFile> configList = new ArrayList<>();
        Instance instData;
        final Path instanceFile = installPath.resolve("instance.json");
        try {
            BufferedReader reader = Files.newBufferedReader(instanceFile, StandardCharsets.UTF_8);
            instData = gson.fromJson(reader, Instance.class);
            reader.close();
        } catch (IOException ioe) {
            instData = new Instance();
        }
        Set<String> digests = new HashSet<>();
        List<Module> fullModList = new ArrayList<>();
        fullModList.addAll(pack.getModules().values());
        for (Module mod : fullModList) {
            if (!mod.getMD5().isEmpty()) {
                digests.add(mod.getMD5());
            }
            for (ConfigFile cf : mod.getConfigs()) {
                if (!cf.getMD5().isEmpty()) {
                    digests.add(cf.getMD5());
                }
            }
            for (GenericModule sm : mod.getSubmodules()) {
                if (!sm.getMD5().isEmpty()) {
                    digests.add(sm.getMD5());
                }
            }
        }
        instData.setHash(MCUpdater.calculateGroupHash(digests));

        for (Map.Entry<String, Module> entry : pack.getModules().entrySet()) {
            if (entry.getValue().isSideValid(ModSide.SERVER)) {
                modList.add(entry.getValue());
                if (entry.getValue().hasSubmodules()) {
                    for (GenericModule submod : entry.getValue().getSubmodules()) {
                        if (submod.isSideValid(ModSide.SERVER)) {
                            modList.add(submod);
                        }
                    }
                }
                if (entry.getValue().hasConfigs()) {
                    configList.addAll(entry.getValue().getConfigs());
                }
            }
        }
        System.out.println();
        try {
            MCUpdater.getInstance().installMods(pack, modList, configList, installPath, options.has("clean"), instData, ModSide.SERVER );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void setStatus(String string) {

    }

    @Override
    public void log(String msg) {
        System.out.println(msg);
    }

    @Override
    public Profile requestLogin(String username) {
        return null;
    }

    @Override
    public DownloadQueue submitNewQueue(String queueName, String parent, Collection<Downloadable> files, File basePath, File cachePath) {
        DownloadQueue newQueue = new DownloadQueue(queueName, parent, this, files, basePath, cachePath);
        queues.add(newQueue);
        return newQueue;
    }

    @Override
    public DownloadQueue submitAssetsQueue(String queueName, String parent, MinecraftVersion version) {
        return null;
    }

    @Override
    public void alert(String msg) {
        baseLogger.warning(msg);
    }

    @Override
    public void onQueueFinished(DownloadQueue queue) {
        System.out.println(queue.getName() + " has finished.");
        queues.remove(queue);
        if (queues.size() == 0) {
            MinecraftVersion mcVersion = MinecraftVersion.loadVersion(pack.getVersion());
            StringBuilder clArgs = new StringBuilder();
            List<String> jreArgs = new ArrayList<>();
            List<String> libs = new ArrayList<>();
            Library lib = new Library();
            lib.setName("net.sf.jopt-simple:jopt-simple:4.5");
            String key = StringUtils.join(Arrays.copyOfRange(lib.getName().split(":"),0,2),":");
            if (pack.getLibOverrides().containsKey(key)) {
                lib.setName(pack.getLibOverrides().get(key));
            }
            if (lib.validForOS() && !lib.hasNatives()) {
                libs.add(lib.getFilename());
            }

            jreArgs.add("java");
            for (GenericModule entry : modList) {
                if (entry.getModType().equals(ModType.Library)) {
                    libs.add(entry.getId() + ".jar");
                }
                if (!entry.getLaunchArgs().isEmpty()) {
                    clArgs.append(" ").append(entry.getLaunchArgs());
                }
                if (!entry.getJreArgs().isEmpty()) {
                    jreArgs.addAll(Arrays.asList(entry.getJreArgs().split(" ")));
                }
            }
            jreArgs.add("-cp");
            StringBuilder classpath = new StringBuilder();
            for (String entry : libs) {
                classpath.append(installPath.resolve("lib").resolve(entry).toString()).append(MCUpdater.cpDelimiter());
            }
            classpath.append(installPath.resolve("minecraft_server.jar").toString());
            jreArgs.add(classpath.toString());
            jreArgs.add("cpw.mods.fml.relauncher.ServerLaunchWrapper");
            String[] fieldArr = clArgs.toString().split(" ");
            jreArgs.addAll(Arrays.asList(fieldArr));
            System.out.println("Command-line to launch:");
            System.out.println(StringUtils.join(jreArgs," "));
        }
    }

    @Override
    public void onQueueProgress(DownloadQueue queue) {

    }

    @Override
    public void printMessage(String msg) {
        System.out.println("A tracker says: " + msg);
    }
}
