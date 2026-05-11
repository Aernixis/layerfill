package com.layerfill;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("layerfill")
public class LayerFillMod {

    public static final String MOD_ID = "layerfill";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public LayerFillMod(IEventBus modEventBus) {
        LOGGER.info("[LayerFill] Layer Fill mod loaded. Water will now fill containers from the bottom up.");
    }
}
