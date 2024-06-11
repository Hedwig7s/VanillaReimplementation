package net.minestom.vanilla.generation;

import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.DimensionType;
import net.minestom.vanilla.VanillaReimplementation;
import net.minestom.vanilla.datapack.Datapack;
import net.minestom.vanilla.datapack.DatapackLoadingFeature;
import net.minestom.vanilla.datapack.worldgen.NoiseSettings;
import net.minestom.vanilla.instance.SetupVanillaInstanceEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class VanillaWorldGenerationFeature implements VanillaReimplementation.Feature {

    @Override
    public void hook(@NotNull HookContext context) {
        context.vri().process().eventHandler().addListener(SetupVanillaInstanceEvent.class, event -> {

            NamespaceID plains = NamespaceID.from("minecraft:plains");
            DatapackLoadingFeature datapackLoading = context.vri().feature(DatapackLoadingFeature.class);
            Datapack datapack = datapackLoading.current();

            Datapack.NamespacedData data = datapack.namespacedData().get("minecraft");
            if (data == null) {
                throw new IllegalStateException("minecraft namespace not found");
            }

            NoiseSettings settings = data.world_gen().noise_settings().file("overworld.json");
//            BiomeSource.fromJson()

            DimensionType dimensionType = context.vri().process().dimensionType().get(event.getInstance().getDimensionType());
            ThreadLocal<NoiseChunkGenerator> generators = ThreadLocal.withInitial(() -> new NoiseChunkGenerator(datapack, (x, y, z, sampler) -> plains, settings, dimensionType));
            event.getInstance().setGenerator(unit -> generators.get().generate(unit));
        });
    }

    @Override
    public @NotNull NamespaceID namespaceId() {
        return NamespaceID.from("vri:worldgeneration");
    }

    @Override
    public @NotNull Set<Class<? extends VanillaReimplementation.Feature>> dependencies() {
        return Set.of(DatapackLoadingFeature.class);
    }
}
