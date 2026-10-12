package com.smprules.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class SMPRulesClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        System.out.println("[SMPRulesMod] Registering Client Minimap Overlay & Coordinates display...");
        HudRenderCallback.EVENT.register(new MinimapOverlay());
    }
}
