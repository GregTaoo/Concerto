package top.gregtao.concerto.mixin;

import com.google.common.collect.Multimap;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {

    @Accessor
    List<SoundInstanceListener> getListeners();

    @Accessor
    SoundListener getListener();

    @Accessor
    Channel getChannel();

    @Accessor
    Map<SoundInstance, Integer> getSoundEndTicks();

    @Accessor
    int getTicks();

    @Accessor
    Map<SoundInstance, Channel.SourceManager> getSources();

    @Accessor
    Multimap<SoundCategory, SoundInstance> getSounds();

    @Accessor
    List<TickableSoundInstance> getTickingSounds();
}
