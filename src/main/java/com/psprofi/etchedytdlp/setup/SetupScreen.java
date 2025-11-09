package com.psprofi.etchedytdlp.setup;

import com.psprofi.etchedytdlp.YouTube.YtDlpManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public class SetupScreen extends Screen {
    private float progress = 0f;
    private Component status = Component.translatable("etchedytdlp.setupscreen.start");
    private Button closeButton;
    private boolean setupDone = false;

    public SetupScreen() {
        super(Component.translatable("etchedytdlp.setupscreen.title"));
    }

    @Override
    protected void init() {
        super.init();

        // кнопка-хрестик у правому верхньому куті
        int x = this.width - 30;
        int y = 10;
        closeButton = Button.builder(Component.literal("✕"), (b) -> {
            Minecraft.getInstance().setScreen(null);
        }).bounds(x, y, 20, 20).build();
        this.addRenderableWidget(closeButton);

        new Thread(this::runSetup, "etchedytdlp-setup-thread").start();
    }

    private void runSetup() {
        try {
            updateStatus(Component.translatable("etchedytdlp.setupscreen.check"));
            if (YtDlpManager.isFullyInstalled()) {
                finishSetup();
                return;
            }

            if (!YtDlpManager.isInstalled()) {
                updateStatus(Component.translatable("etchedytdlp.setupscreen.downloadytdlp"));
                downloadSimulate(0.5f);
            }

            if (!YtDlpManager.isFfmpegInstalled()) {
                updateStatus(Component.translatable("etchedytdlp.setupscreen.downloadffmpeg"));
                downloadSimulate(0.5f);
            }

            updateStatus(Component.translatable("etchedytdlp.setupscreen.finalizing"));
            YtDlpManager.ensureInstalled(null);

            progress = 1f;
            updateStatus(Component.translatable("etchedytdlp.setupscreen.complete"));
            Thread.sleep(800);

            setupDone = true;
            finishSetup();

        } catch (IOException | InterruptedException e) {
            updateStatus(Component.translatable("etchedytdlp.setupscreen.failed", e.getMessage()));
        }
    }

    private void downloadSimulate(float delta) throws InterruptedException {
        for (int i = 0; i <= 50; i++) {
            this.progress = Math.min(this.progress + delta / 50f, 1f);
            Thread.sleep(50);
        }
    }

    private void updateStatus(Component msg) {
        this.status = msg;
        System.out.println("[Etched YT-DLP] " + msg.getString());
    }

    private void finishSetup() {
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(null);

            // Повідомлення у чат
            if (setupDone && mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("etchedytdlp.setupscreen.completechat")
                                .withStyle(ChatFormatting.GREEN),
                        false
                );
            }
        });
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gui);
        int cx = width / 2;
        int cy = height / 2;

        // 🔹 Малюємо заголовок як перекладний текст
        gui.drawCenteredString(this.font,
                Component.translatable("etchedytdlp.setupscreen.instaling"),
                cx, cy - 40, 0xFFFFFF);

        // 🔹 Прогресбар
        int barWidth = 200;
        int filled = (int) (barWidth * progress);
        gui.fill(cx - barWidth / 2, cy - 10, cx + barWidth / 2, cy + 10, 0xFF333333);
        gui.fill(cx - barWidth / 2, cy - 10, cx - barWidth / 2 + filled, cy + 10, 0xFF55FF55);

        // 🔹 Поточний статус
        gui.drawCenteredString(this.font, this.status, cx, cy + 20, 0xAAAAAA);

        super.render(gui, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true; // дозволяє закрити ESC
    }
}
