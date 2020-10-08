package com.example.examplemod;

import io.netty.handler.logging.LogLevel;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.command.arguments.EntityAnchorArgument;
import net.minecraft.entity.*;
import net.minecraft.entity.EntityPredicate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWMonitorCallbackI;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.CallbackI;


import javax.swing.*;
import java.awt.*;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("examplemod")
public class ExampleMod
{
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();
    long windowHandle;
    Minecraft minecraft;
    public ExampleMod() {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the enqueueIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        minecraft = Minecraft.getInstance();
        windowHandle = minecraft.getMainWindow().getHandle();


    }
    int[][] heights = new int[32][32];
    int[][] heightDiffs = new int[32][32];
    List<Entity> entitiesWithinAABB;  
    PlayerEntity player;

    JPanel panel;

    static {
        System.setProperty("java.awt.headless", "false");
    }
    Robot robo;

    private void setup(final FMLCommonSetupEvent event)
    {
        // some preinit code
        LOGGER.info("HELLO FROM PREINIT");
        LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());

        JFrame jFrame = new JFrame("map plotter");
        final int blockSideLength = 16;
        jFrame.setBounds(300,300,blockSideLength * 34, blockSideLength * 34);
        jFrame.setSize(blockSideLength * 34, blockSideLength * 34);
        jFrame.setBackground(Color.cyan);
        panel = new JPanel(){

            @Override
            public void paint(Graphics graphics) {
                super.paint(graphics);
                Graphics2D g2d = (Graphics2D)graphics;
                for(int x = 0; x < 32; x++) {
                    for(int z = 0; z < 32; z++) {
                        switch (heightDiffs[x][z]) {
                            case 0:
                            case 1:
                                g2d.setColor(Color.green);
                                g2d.fillRect( blockSideLength * (x+1), blockSideLength * (z+1), blockSideLength, blockSideLength);
                                break;
                            case 2:
                                g2d.setColor(Color.yellow);
                                g2d.fillRect( blockSideLength * (x+1), blockSideLength * (z+1), blockSideLength, blockSideLength);
                                break;
                            case -1:
                                g2d.setColor(Color.lightGray);
                                g2d.fillRect( blockSideLength * (x+1), blockSideLength * (z+1), blockSideLength, blockSideLength);
                                break;
                            default:
                                g2d.setColor(new Color((int)(255 / (heightDiffs[x][z] * 0.5f)), 48 - heightDiffs[x][z] > 0 ? 48 / heightDiffs[x][z] : 0 ,0));
                                g2d.fillRect( blockSideLength * (x+1), blockSideLength * (z+1), blockSideLength, blockSideLength);
                        }
                        if(x == 16 && z == 16){
                            g2d.setColor(Color.pink);
                            g2d.fillOval( blockSideLength * (x) + 4, blockSideLength * (z) + 4, blockSideLength - 4, blockSideLength -  4);
                        }
                    }
                }
                if(entitiesWithinAABB != null && player != null) {
                    for (Entity entity: entitiesWithinAABB
                         ) {
                        Vector3d diff = entity.getPositionVec().subtract(player.getPositionVec());
                        double fx = 16 + diff.x;
                        double fz = 16 + diff.z;
                        Color paintColor = Color.black;
                        if(entity instanceof ArrowEntity){
                            paintColor = Color.yellow;
                            if(entity.getPositionVec().y < heights[(int)fx][(int)fz]) paintColor.darker();
                            double lx = 16 + Math.pow(entity.lastTickPosX - player.getPositionVec().x, 1);
                            double lz = 16 + Math.pow(entity.lastTickPosZ - player.getPositionVec().z, 1);

                            if(Math.sqrt(Math.pow(lx-fx, 2) + Math.pow(lz-fz, 2)) < 0.05) continue;

                            g2d.drawLine((int)(lx * blockSideLength),(int)(lz * blockSideLength),(int)(fx * blockSideLength),(int)(fz * blockSideLength));
                            g2d.setColor(Color.white);
                            g2d.drawLine((int)(fx * blockSideLength),(int)(fz * blockSideLength), blockSideLength * 16, blockSideLength * 16);

                            continue;
                        }
                        if(entity instanceof MobEntity && ! (entity instanceof VillagerEntity)) paintColor = Color.red;
                        else if(entity instanceof AnimalEntity) paintColor = Color.lightGray;
                        else {
                            g2d.setColor(Color.magenta);
                            g2d.drawString("?", (int)(16 * fx), (int)(16 * fz));
                            continue;
                        }
                        if(entity.getPositionVec().y < heights[(int)fx][(int)fz]) paintColor.darker();
                        g2d.setColor(Color.black);
                        g2d.fillOval((int)(fx*blockSideLength) - 6, (int)(fz*blockSideLength) - 6, blockSideLength - 8, blockSideLength -8);
                        g2d.setColor(paintColor);
                        g2d.fillOval((int)(fx*blockSideLength) - 8, (int)(fz*blockSideLength) - 8, blockSideLength - 6, blockSideLength - 6);


                    }
                }


            }

        };
        jFrame.getContentPane().add(panel);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
//        jFrame.setUndecorated(false);
        jFrame.setLocationByPlatform(true);
        jFrame.setLocation(300,300);
        jFrame.setFocusable(true);
        jFrame.pack();

        jFrame.setVisible(true);
        try {
            robo = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        // do something that can only be done on the client
        LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().gameSettings);
    }

    private void enqueueIMC(final InterModEnqueueEvent event)
    {
        // some example code to dispatch IMC to another mod
        InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the MDK"); return "Hello world";});
    }

    private void processIMC(final InterModProcessEvent event)
    {
        // some example code to receive and process InterModComms from other mods
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m->m.getMessageSupplier().get()).
                collect(Collectors.toList()));
    }
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        // do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
            // register a new block here
            LOGGER.info("HELLO from Register Block");
        }

    }
    float t;
    final static int[][] neighbors = new int[][]{{0,1},{0,1},{0,1},{0,1}};
    synchronized void updateEntities(Vector3d playerPosition, World world) {
        entitiesWithinAABB = world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(playerPosition.subtract(16, 16, 16), playerPosition.add(15, 15, 15)), new Predicate<Entity>() {
            @Override
            public boolean test(Entity entity) {
                if(entity instanceof LivingEntity) return true;
                if(entity instanceof ArrowEntity) return true;
                return false;
            }
        });
//        world.getEntitiesWithinAABB(ArrowEntity.class, new AxisAlignedBB(playerPosition.subtract(16, 16, 16), playerPosition.add(15, 15, 15))).forEach((ArrowEntity ae) ->{
//            if(ae.getPositionVec().distanceTo(playerPosition) > 10) return;
//            if(new Vector3d(ae.lastTickPosX, ae.lastTickPosY, ae.lastTickPosZ).distanceTo(playerPosition) > ae.getPositionVec().distanceTo(playerPosition)) {
//                // approaching
//                player.moveRelative(0.0075f * (float)(2 + Math.atan(5 - (ae.getPositionVec().distanceTo(playerPosition))) ), ae.getPositionVec().add(4,0,0));
////                Vector3d fpos = new Vector3d(ae.getPositionVec().x, playerPosition.y, ae.getPositionVec().z);
////                if(ae.getPositionVec().distanceTo(playerPosition) > 1.28f)
////                    player.lookAt(EntityAnchorArgument.Type.EYES, fpos);
////                player.getHeldItemOffhand().useItemRightClick(world,player, Hand.OFF_HAND);
//                //player.jump();
//                LOGGER.info("Arrow detected; avoided.");
//            }else {
//
//            }
//        });

    }
    @SubscribeEvent
    public void somethingRandom(TickEvent tickEvent) {
        if(minecraft.world != null && minecraft.player != null) {
            World world = minecraft.world;
            player = minecraft.player;
            Vector3d playerPosition = player != null ? player.getPositionVec() : null;
            Chunk currentChunk = world.getChunkAt(new BlockPos((int)playerPosition.x, (int)playerPosition.y, (int)playerPosition.z));
            for(int x = -16; x < 16; x++) {
                for(int z = -16; z < 15;z++) {
                    int newx = x + (int)playerPosition.x;
                    int newz = z + (int)playerPosition.z;
                    heights[x + 16][z + 16] = world.getHeight(Heightmap.Type.MOTION_BLOCKING , newx,newz);
                    heightDiffs[x + 16][z + 16] = (int) Math.abs(heights[x + 16][z + 16] - playerPosition.y);
                }
            }
            updateEntities(playerPosition, world);
//            getLock();
//            entitiesWithinAABB = world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(playerPosition.subtract(16, 16, 16), playerPosition.add(15, 15, 15)));
//            releaseLock();
            if(panel != null) panel.repaint();
        }
    }
}
