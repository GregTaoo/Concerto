package top.gregtao.concerto.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class EditMusicTitleScreen extends ConcertoScreen {
    private final String currentTitle;
    private final Consumer<String> onSave;
    private EditBox titleInput;

    public EditMusicTitleScreen(String currentTitle, Consumer<String> onSave, Screen parent) {
        super(Component.translatable("concerto.screen.edit_music_title"), parent);
        this.currentTitle = currentTitle;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        super.init();
        this.titleInput = new EditBox(this.font, this.width / 2 - 125, this.height / 2 - 15, 250, 20,
                Component.translatable("concerto.screen.edit_music_title.hint"));
        this.titleInput.setMaxLength(1024);
        this.titleInput.setValue(this.currentTitle);
        this.addRenderableWidget(this.titleInput);
        this.setInitialFocus(this.titleInput);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.save"), ignored -> this.save())
                .pos(this.width / 2 - 102, this.height / 2 + 15).size(100, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> this.onClose())
                .pos(this.width / 2 + 2, this.height / 2 + 15).size(100, 20).build());
    }

    private void save() {
        String title = this.titleInput.getValue().trim();
        if (title.isEmpty()) return;
        this.onSave.accept(title);
        this.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER && this.titleInput.isFocused()) {
            this.save();
            return true;
        }
        return super.keyPressed(event);
    }
}
