package dev.nationsforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class NationKeybinds {

    public static final KeyMapping OPEN_NATIONS = new KeyMapping(
            "key.nationsforge.open_nations",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.nationsforge");
}
