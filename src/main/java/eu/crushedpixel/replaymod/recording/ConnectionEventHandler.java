package eu.crushedpixel.replaymod.recording;

import eu.crushedpixel.replaymod.ReplayMod;
import eu.crushedpixel.replaymod.chat.ChatMessageHandler.ChatMessageType;
import eu.crushedpixel.replaymod.utils.ReplayFileIO;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class ConnectionEventHandler {

    public static final String TEMP_FILE_EXTENSION = ".tmcpr";
    public static final String JSON_FILE_EXTENSION = ".json";
    public static final String ZIP_FILE_EXTENSION = ".mcpr";
    private static final String decoderKey = "decoder";
    private static final String packetHandlerKey = "packet_handler";
    private static final String DATE_FORMAT = "yyyy_MM_dd_HH_mm_ss";
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
    private static PacketListener packetListener = null;
    private static boolean isRecording = false;
    private File currentFile;
    private String fileName;

    private static final Logger logger = LogManager.getLogger();

    public static boolean isRecording() {
        return isRecording;
    }

    public static void insertPacket(Packet packet) {
        if(!isRecording || packetListener == null) {
            String reason = isRecording ? " (recording)" : " (null)";
            logger.error("Invalid attempt to insert Packet!" + reason);
            return;
        }
        try {
            packetListener.saveOnly(packet);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onConnectedToServerEvent(ClientConnectedToServerEvent event) {
        ReplayMod.chatMessageHandler.initialize();
        ReplayMod.recordingHandler.resetVars();

        try {
            if(event.isLocal) {
                if(!ReplayMod.replaySettings.isEnableRecordingSingleplayer()) {
                    logger.info("Singleplayer Recording is disabled");
                    return;
                }
            } else {
                if(!ReplayMod.replaySettings.isEnableRecordingServer()) {
                    logger.info("Multiplayer Recording is disabled");
                    return;
                }
            }
            NetworkManager nm = event.manager;
            String worldName = "";
            if(!event.isLocal) {
                worldName = ((InetSocketAddress) nm.getRemoteAddress()).getHostName();
            }
            Channel channel = nm.channel();
            ChannelPipeline pipeline = channel.pipeline();

            List<String> channelHandlerKeys = new ArrayList<String>();
            Iterator<Entry<String, ChannelHandler>> it = pipeline.iterator();
            while(it.hasNext()) {
                Entry<String, ChannelHandler> entry = it.next();
                channelHandlerKeys.add(entry.getKey());
            }

            File folder = ReplayFileIO.getReplayFolder();

            fileName = sdf.format(Calendar.getInstance().getTime());
            currentFile = new File(folder, fileName + TEMP_FILE_EXTENSION);

            currentFile.createNewFile();

            PacketListener insert = null;

            pipeline.addBefore(packetHandlerKey, "replay_recorder", insert = new PacketListener
                    (currentFile, fileName, worldName, System.currentTimeMillis(), event.isLocal));
            ReplayMod.chatMessageHandler.addLocalizedChatMessage("replaymod.chat.recordingstarted", ChatMessageType.INFORMATION);
            isRecording = true;

            final PacketListener listener = insert;

            if(insert != null && event.isLocal) {
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        String worldName = null;
                        while(worldName == null) {
                            try {
                                worldName = MinecraftServer.getServer().getWorldName();
                                listener.setWorldName(worldName);
                                return;

                            } catch(Exception e) {
                                try {
                                    Thread.sleep(100);
                                } catch(InterruptedException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }

                    }
                }).start();
            }

            packetListener = listener;

        } catch(Exception e) {
            ReplayMod.chatMessageHandler.addLocalizedChatMessage("replaymod.chat.recordingfailed", ChatMessageType.WARNING);
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onDisconnectedFromServerEvent(ClientDisconnectionFromServerEvent event) {
        isRecording = false;
        packetListener = null;
        ReplayMod.chatMessageHandler.stop();
    }
}
