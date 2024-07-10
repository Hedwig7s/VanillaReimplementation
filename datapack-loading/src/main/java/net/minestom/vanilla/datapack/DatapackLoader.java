package net.minestom.vanilla.datapack;

import com.squareup.moshi.*;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.utils.math.FloatRange;
import net.minestom.vanilla.datapack.loot.NBTPath;
import net.minestom.vanilla.datapack.trims.TrimMaterial;
import net.minestom.vanilla.datapack.trims.TrimPattern;
import net.minestom.vanilla.datapack.worldgen.*;
import net.minestom.vanilla.datapack.worldgen.math.CubicSpline;
import net.minestom.vanilla.datapack.worldgen.noise.Noise;
import net.minestom.vanilla.datapack.worldgen.random.WorldgenRandom;
import net.minestom.vanilla.files.ByteArray;
import net.minestom.vanilla.files.FileSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.DimensionType;
import net.minestom.vanilla.datapack.advancement.Advancement;
import net.minestom.vanilla.datapack.json.JsonUtils;
import net.minestom.vanilla.datapack.loot.LootTable;
import net.minestom.vanilla.datapack.loot.context.LootContext;
import net.minestom.vanilla.datapack.loot.function.LootFunction;
import net.minestom.vanilla.datapack.loot.function.Predicate;
import net.minestom.vanilla.datapack.number.NumberProvider;
import net.minestom.vanilla.datapack.recipe.Recipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.minestom.vanilla.datapack.Datapack.*;

public class DatapackLoader {

    private static final Moshi moshi = createMoshiWithAdaptors();

    DatapackLoader() {
    }

    private static Moshi createMoshiWithAdaptors() {
        Moshi.Builder builder = new Moshi.Builder();

        // Native
        register(builder, UUID.class, DatapackLoader::uuidFromJson);

        // json utils
        builder.add((type, annotations, moshi) -> {
            if (typeDoesntMatch(type, JsonUtils.SingleOrList.class)) return null;
            Type elementType = Types.collectionElementType(type, Collection.class);
            return new JsonAdapter<JsonUtils.SingleOrList<?>>() {
                @Override
                public JsonUtils.SingleOrList<?> fromJson(@NotNull JsonReader reader) throws IOException {
                    return JsonUtils.SingleOrList.fromJson(elementType, reader);
                }

                @Override
                public void toJson(@NotNull JsonWriter writer, JsonUtils.SingleOrList<?> value) {
                }
            };
        });

        // Minestom
        register(builder, CompoundBinaryTag.class, DatapackLoader::nbtCompoundFromJson);
        register(builder, Block.class, DatapackLoader::blockFromJson);
        register(builder, Enchantment.class, DatapackLoader::enchantmentFromJson);
        register(builder, EntityType.class, DatapackLoader::entityTypeFromJson);
        register(builder, Material.class, DatapackLoader::materialFromJson);
        register(builder, Component.class, reader -> {
            GsonComponentSerializer serializer = GsonComponentSerializer.gson();
            return serializer.deserialize(reader.nextSource().readUtf8());
        });
        register(builder, NamespaceID.class, reader -> NamespaceID.from(reader.nextString()));
        register(builder, FloatRange.class, DatapackLoader::floatRangeFromJson);
        register(builder, DimensionType.class, DatapackLoader::dimensionTypeFromJson);

        // Misc
        register(builder, DoubleList.class, DatapackLoader::doubleListFromJson);
        // VRI Datapack
        register(builder, Advancement.Trigger.class, Advancement.Trigger::fromJson);
        register(builder, BlockState.class, BlockState::fromJson);
        register(builder, LootContext.Trait.class, LootContext.Trait::fromJson);
        register(builder, LootFunction.class, LootFunction::fromJson);
        register(builder, Predicate.class, Predicate::fromJson);
        register(builder, Predicate.BlockStateProperty.Property.class, Predicate.BlockStateProperty.Property::fromJson);
        register(builder, Predicate.EntityScores.Score.class, Predicate.EntityScores.Score::fromJson);
        register(builder, Predicate.TimeCheck.Value.class, Predicate.TimeCheck.Value::fromJson);
        register(builder, Predicate.ValueCheck.Range.class, Predicate.ValueCheck.Range::fromJson);
        register(builder, NumberProvider.class, NumberProvider.Double::fromJson);
        register(builder, NumberProvider.Double.class, NumberProvider.Double::fromJson);
        register(builder, NumberProvider.Int.class, NumberProvider.Int::fromJson);
        register(builder, LootTable.Pool.Entry.class, LootTable.Pool.Entry::fromJson);
        register(builder, LootFunction.ApplyBonus.class, LootFunction.ApplyBonus::fromJson);
        register(builder, LootFunction.CopyNbt.Source.class, LootFunction.CopyNbt.Source::fromJson);
        register(builder, LootFunction.CopyNbt.Operation.class, LootFunction.CopyNbt.Operation::fromJson);
        register(builder, LootFunction.LimitCount.Limit.class, LootFunction.LimitCount.Limit::fromJson);
        register(builder, Recipe.class, Recipe::fromJson);
        register(builder, Recipe.Ingredient.class, Recipe.Ingredient::fromJson);
        register(builder, Recipe.Ingredient.Single.class, Recipe.Ingredient.Single::fromJson);
        register(builder, NBTPath.class, NBTPath::fromJson);
        register(builder, NBTPath.Single.class, NBTPath.Single::fromJson);
        register(builder, DensityFunction.class, DensityFunction::fromJson);
        register(builder, Noise.class, Noise::fromJson);
        register(builder, NoiseSettings.SurfaceRule.class, NoiseSettings.SurfaceRule::fromJson);
        register(builder, NoiseSettings.SurfaceRule.SurfaceRuleCondition.class, NoiseSettings.SurfaceRule.SurfaceRuleCondition::fromJson);
        register(builder, VerticalAnchor.class, VerticalAnchor::fromJson);
        register(builder, CubicSpline.class, CubicSpline::fromJson);
        register(builder, DensityFunction.OldBlendedNoise.class, DensityFunction.OldBlendedNoise::fromJson);
        register(builder, Tag.TagValue.class, Tag.TagValue::fromJson);
        register(builder, Tag.TagValue.ObjectOrTagReference.class, Tag.TagValue.ObjectOrTagReference::fromJson);
        register(builder, Biome.Effects.Particle.Options.class, Biome.Effects.Particle.Options::fromJson);
        register(builder, Biome.Sound.class, Biome.Sound::fromJson);
        register(builder, Carver.class, Carver::fromJson);
        register(builder, FloatProvider.class, FloatProvider::fromJson);
        register(builder, Biome.CarversList.class, Biome.CarversList::fromJson);
        register(builder, Biome.CarversList.Single.class, Biome.CarversList.Single::fromJson);
        register(builder, HeightProvider.class, HeightProvider::fromJson);
        return builder.build();
    }

    static <T> FileSystem<T> parseJsonFolder(FileSystem<ByteArray> source, String path, Function<String, T> converter) {
        return source.folder(path).map(FileSystem.BYTES_TO_STRING).map(converter);
    }

    public static <T> Function<String, T> adaptor(Class<T> clazz) {
        return str -> {
            try {
                return moshi.adapter(clazz).fromJson(str);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static final ThreadLocal<LoadingContext> contextPool = new ThreadLocal<>();

    public static LoadingContext loading() {
        LoadingContext context = contextPool.get();
        if (context == null) {
            return STATIC_CONTEXT;
        }
        return context;
    }

    public interface LoadingContext {
        WorldgenRandom random();

        void whenFinished(Consumer<DatapackFinisher> finishAction);

        default boolean isStatic() {
            return false;
        }
    }

    private static final LoadingContext STATIC_CONTEXT = new LoadingContext() {
        @Override
        public WorldgenRandom random() {
            return WorldgenRandom.xoroshiro(0);
        }

        @Override
        public void whenFinished(Consumer<DatapackFinisher> finishAction) {
            throw new RuntimeException(new IllegalAccessException("Not in a datapack loading context"));
        }

        @Override
        public boolean isStatic() {
            return true;
        }
    };

    public interface DatapackFinisher {
        Datapack datapack();
    }

    public Datapack load(FileSystem<ByteArray> source) {

        // Default
        McMeta mcmeta;
        mcmeta = !source.hasFile("pack.mcmeta") ? new McMeta() : source.map(FileSystem.BYTES_TO_STRING).map(adaptor(McMeta.class)).file("pack.mcmeta");
        @Nullable ByteArray pack_png = !source.hasFile("pack.png") ? null : source.file("pack.png");
//        ImageIO.read(pack_png.toStream());

        // Load this datapack on this thread, so that we can use the thread-local contextPool
        WorldgenRandom loading = WorldgenRandom.xoroshiro(0);
        Queue<Consumer<DatapackFinisher>> finishers = new ArrayDeque<>();
        LoadingContext context = new LoadingContext() {
            @Override
            public WorldgenRandom random() {
                return loading;
            }

            @Override
            public void whenFinished(Consumer<DatapackFinisher> finishAction) {
                finishers.add(finishAction);
            }
        };
        contextPool.set(context);

        Map<String, NamespacedData> namespace2data;
        {
            namespace2data = new HashMap<>();

            for (String namespace : source.folders()) {
                FileSystem<ByteArray> dataFolder = source.folder(namespace).inMemory();

                FileSystem<Advancement> advancements = parseJsonFolder(dataFolder, "advancements", adaptor(Advancement.class));
                FileSystem<McFunction> functions = parseJsonFolder(dataFolder, "functions", McFunction::fromString);
                FileSystem<LootFunction> item_modifiers = parseJsonFolder(dataFolder, "item_modifiers", adaptor(LootFunction.class));
                FileSystem<LootTable> loot_tables = parseJsonFolder(dataFolder, "loot_tables", adaptor(LootTable.class));
                FileSystem<Predicate> predicates = parseJsonFolder(dataFolder, "predicates", adaptor(Predicate.class));
                FileSystem<Recipe> recipes = parseJsonFolder(dataFolder, "recipes", adaptor(Recipe.class));
                FileSystem<Structure> structures = dataFolder.folder("structures").map(Structure::fromInput);
                FileSystem<ChatType> chat_type = parseJsonFolder(dataFolder, "chat_type", adaptor(ChatType.class));
                FileSystem<DamageType> damage_type = parseJsonFolder(dataFolder, "damage_type", adaptor(DamageType.class));
                FileSystem<Tag> tags = parseJsonFolder(dataFolder, "tags", adaptor(Tag.class));
                FileSystem<Dimension> dimensions = parseJsonFolder(dataFolder, "dimension", adaptor(Dimension.class));
                FileSystem<DimensionType> dimension_type = parseJsonFolder(dataFolder, "dimension_type", adaptor(DimensionType.class));
                FileSystem<TrimPattern> trim_pattern = parseJsonFolder(dataFolder, "trim_pattern", adaptor(TrimPattern.class));
                FileSystem<TrimMaterial> trim_material = parseJsonFolder(dataFolder, "trim_material", adaptor(TrimMaterial.class));
                Datapack.WorldGen world_gen = Datapack.WorldGen.from(dataFolder.folder("worldgen"));

                NamespacedData data = new NamespacedData(advancements, functions, item_modifiers, loot_tables,
                        predicates, recipes, structures, chat_type, damage_type, tags, dimensions, dimension_type,
                        trim_pattern, trim_material, world_gen);
                namespace2data.put(namespace, data);
            }
        }

        var copy = namespace2data.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().cache()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        Datapack datapack = new Datapack() {
            @Override
            public Map<String, NamespacedData> namespacedData() {
                return copy;
            }

            @Override
            public String toString() {
                return "Datapack{" +
                        "namespace2data=" + copy +
                        '}';
            }
        };

        // new we can finish the datapack
        while (!finishers.isEmpty()) {
            finishers.poll().accept(() -> datapack);
        }
        contextPool.remove();
        return datapack;
    }

    private static <T> void register(Moshi.Builder builder, Class<T> clazz, JsonAdapter<T> adapter) {
        builder.add((type, annotations, moshi) -> {
            if (typeDoesntMatch(type, clazz)) return null;
            return adapter;
        });
    }

    private static <T> void register(Moshi.Builder builder, Class<T> clazz, IoFunction<JsonReader, T> reader) {
        register(builder, clazz, new IoJsonAdaptor<>(reader));
    }

    private static class IoJsonAdaptor <T> extends JsonAdapter<T> {
        private final IoFunction<JsonReader, T> reader;

        public IoJsonAdaptor(IoFunction<JsonReader, T> reader) {
            this.reader = reader;
        }

        @Override
        public T fromJson(JsonReader jsonReader) throws IOException {
            return reader.apply(jsonReader);
        }

        @Override
        public void toJson(JsonWriter writer, T value) throws IOException {
        }
    }

    public interface IoFunction<T, R> {
        R apply(T t) throws IOException;
    }

    public static Moshi moshi() {
        return moshi;
    }

    public static <T> JsonUtils.IoFunction<JsonReader, T> moshi(Class<? extends T> clazz) {
        return moshi().adapter(clazz)::fromJson;
    }

    public static <T> JsonUtils.IoFunction<JsonReader, T> moshi(Type type) {
        JsonUtils.IoFunction<JsonReader, Object> function = moshi().adapter(type)::fromJson;
        //noinspection unchecked
        return (JsonUtils.IoFunction<JsonReader, T>) function;
    }

    private static CompoundBinaryTag nbtCompoundFromJson(JsonReader reader) throws IOException {
        String json = reader.nextSource().readUtf8();
        try {
            return TagStringIO.get().asCompound(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static NamespaceID namespaceFromJson(JsonReader reader) throws IOException {
        return NamespaceID.from(reader.nextString());
    }

    private static UUID uuidFromJson(JsonReader reader) throws IOException {
        throw new UnsupportedOperationException("UUIDs are not supported yet");
    }

    private static Block blockFromJson(JsonReader reader) throws IOException {
        return JsonUtils.typeMapMapped(reader, Map.of(
                JsonReader.Token.STRING, json -> Block.fromNamespaceId(json.nextString()),
                JsonReader.Token.BEGIN_OBJECT, json -> DatapackLoader.moshi(BlockState.class).apply(json).toMinestom()
        ));
    }

    private static Enchantment enchantmentFromJson(JsonReader reader) throws IOException {
        String description = null;
        int weight = -1;
        int maxLevel = -1;
        int minBaseCost = -1;
        int minPerLevelAboveCost = -1;
        int maxBaseCost = -1;
        int maxPerLevelAboveCost = -1;
        int anvilCost = -1;
        List<EquipmentSlotGroup> slots = new ArrayList<>();
        DataComponentMap effects = DataComponentMap.EMPTY;
        Map<String, EquipmentSlotGroup> slotMap = new HashMap<>();
        slotMap.put("any", EquipmentSlotGroup.ANY);
        slotMap.put("hand", EquipmentSlotGroup.HAND);
        slotMap.put("mainhand", EquipmentSlotGroup.MAIN_HAND);
        slotMap.put("offhand", EquipmentSlotGroup.OFF_HAND);
        slotMap.put("armor", EquipmentSlotGroup.ARMOR);
        slotMap.put("legs", EquipmentSlotGroup.LEGS);
        slotMap.put("feet", EquipmentSlotGroup.FEET);
        slotMap.put("chest", EquipmentSlotGroup.CHEST);
        slotMap.put("head", EquipmentSlotGroup.HEAD);
        slotMap.put("body", EquipmentSlotGroup.BODY);
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("description")) {
                description = reader.nextString();
            } else if (name.equals("weight")) {
                weight = reader.nextInt();
            } else if (name.equals("max_level")) {
                maxLevel = reader.nextInt();
            } else if (name.equals("min_cost")) {
                reader.beginObject();
                while (reader.hasNext()) {
                    if (reader.nextName().equals("base_cost")) {
                        minBaseCost = reader.nextInt();
                    } else if (reader.nextName().equals("per_level_above_cost")) {
                        minPerLevelAboveCost = reader.nextInt();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else if (name.equals("max_cost")) {
                reader.beginObject();
                while (reader.hasNext()) {
                    if (reader.nextName().equals("base_cost")) {
                        maxBaseCost = reader.nextInt();
                    } else if (reader.nextName().equals("per_level_above_cost")) {
                        maxPerLevelAboveCost = reader.nextInt();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else if (name.equals("anvil_cost")) {
                anvilCost = reader.nextInt();
            } else if (name.equals("slots")) {
                reader.beginArray();

                while (reader.hasNext()) {
                    EquipmentSlotGroup equipmentSlotGroup = slotMap.get(reader.nextName().toLowerCase());
                    if (equipmentSlotGroup == null) {
                        reader.skipValue();
                    }
                    slots.add(equipmentSlotGroup);
                }
                reader.endArray();
            } else if (name.equals("effects")) {
                reader.beginArray();
                while (reader.hasNext()) {
                    DataComponent effect = EffectComponent.fromNamespaceId(reader.nextString());
                    if (effect == null) {
                        reader.skipValue();
                    }
                    effects.set(effect);
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        assert description != null : "Description is missing";
        assert weight > 0 && maxLevel <= 1024 : "Weight is missing or invalid";
        assert maxLevel > 0 && maxLevel < 256 : "Max level is missing or invalid";
        assert minBaseCost > -1 : "Min base cost is missing or invalid";
        assert minPerLevelAboveCost > -1 : "Min per level above cost is missing or invalid";
        assert maxBaseCost > -1 : "Max base cost is missing or invalid";
        assert maxPerLevelAboveCost > -1 : "Max per level above cost is missing or invalid";
        assert anvilCost > -1 : "Anvil cost is missing or invalid";
        assert !slots.isEmpty() : "Slots are missing";
        return Enchantment.builder()
                .description(Component.text(description))
                .weight(weight)
                .maxLevel(maxLevel)
                .minCost(new Enchantment.Cost(minBaseCost, minPerLevelAboveCost))
                .maxCost(new Enchantment.Cost(maxBaseCost, maxPerLevelAboveCost))
                .anvilCost(anvilCost)
                .effects(effects)
                .slots(slots)
                .build();
    }
    private static DimensionType dimensionTypeFromJson(JsonReader reader) throws IOException {
        Boolean ultrawarm = null;
        Boolean natural = null;
        double coordinateScale = -1.0d;
        Boolean hasSkylight = null;
        Boolean hasCeiling = null;
        float ambientLight = -1.0f;
        Long fixedTime = null;
        NumberProvider.Int monsterSpawnLightLevel = null;
        int monsterSpawnBlockLightLimit = -1;
        Boolean piglinSafe = null;
        Boolean bedWorks = null;
        Boolean respawnAnchorWorks = null;
        Boolean hasRaids = null;
        int logicalHeight = -1;
        int minY = -2033;
        int height = -1;
        String infiniburn = "";
        String effects = "minecraft:overworld";
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("ultrawarm")) {
                ultrawarm = reader.nextBoolean();
            } else if (name.equals("natural")) {
                natural = reader.nextBoolean();
            } else if (name.equals("coordinate_scale")) {
                coordinateScale = reader.nextDouble();
            } else if (name.equals("has_skylight")) {
                hasSkylight = reader.nextBoolean();
            } else if (name.equals("has_ceiling")) {
                hasCeiling = reader.nextBoolean();
            } else if (name.equals("ambient_light")) {
                ambientLight = Float.parseFloat(reader.nextString());
            } else if (name.equals("fixed_time")) {
                fixedTime = reader.nextLong();
            } else if (name.equals("monster_spawn_light_level")) {
                monsterSpawnLightLevel = NumberProvider.Int.fromJson(reader);
            } else if (name.equals("monster_spawn_block_light_limit")) {
                monsterSpawnBlockLightLimit = reader.nextInt();
            } else if (name.equals("piglin_safe")) {
                piglinSafe = reader.nextBoolean();
            } else if (name.equals("bed_works")) {
                bedWorks = reader.nextBoolean();
            } else if (name.equals("respawn_anchor_works")) {
                respawnAnchorWorks = reader.nextBoolean();
            } else if (name.equals("has_raids")) {
                hasRaids = reader.nextBoolean();
            } else if (name.equals("logical_height")) {
                logicalHeight = reader.nextInt();
            } else if (name.equals("min_y")) {
                minY = reader.nextInt();
            } else if (name.equals("height")) {
                height = reader.nextInt();
            } else if (name.equals("infiniburn")) {
                infiniburn = reader.nextString();
            } else if (name.equals("effects")) {
                effects = reader.nextString();
            }
        }
        reader.endObject();
        assert ultrawarm != null : "ultrawarm is missing";
        assert natural != null : "natural is missing";
        assert coordinateScale > 0.00001d && coordinateScale < 30000000.0 : "coordinate_scale is missing or invalid";
        assert hasSkylight != null : "has_skylight is missing";
        assert hasCeiling != null : "hasCeiling is missing";
        assert ambientLight > -0.00001f : "ambient_light is missing or invalid";
        // Following are unimplemented
        //assert monsterSpawnLightLever != null && monsterSpawnLightLevel.asInt() > -1 && monsterSpawnLightLevel.asInt() < 16: "monster_spawn_light_level is missing or invalid";
        //assert monsterSpawnBlockLightLimit > -1 && monsterSpawnBlockLightLimit < 16: "monster_spawn_block_light_limit is missing or invalid";
        assert piglinSafe != null : "piglin_safe is missing";
        assert bedWorks != null : "bed_works is missing";
        assert respawnAnchorWorks != null : "respawn_anchor_works is missing";
        assert hasRaids != null : "has_raids is missing";
        assert height >= 16 && height <= 4064 && height % 16 == 0  : "height is missing or invalid";
        assert logicalHeight >= 16 && logicalHeight <= height : "logical_height is missing or invalid";
        assert minY >= -2032 && minY <= 2031 && Math.abs(minY) % 16 == 0 : "min_y is missing or invalid";
        DimensionType.Builder builder = DimensionType.builder()
                .ultrawarm(ultrawarm)
                .natural(natural)
                .coordinateScale(coordinateScale)
                .hasSkylight(hasSkylight)
                .hasCeiling(hasCeiling)
                .ambientLight(ambientLight)
                .piglinSafe(piglinSafe)
                .bedWorks(bedWorks)
                .respawnAnchorWorks(respawnAnchorWorks)
                .hasRaids(hasRaids)
                .height(height)
                .logicalHeight(logicalHeight)
                .minY(minY)
                .infiniburn(infiniburn)
                .effects(effects);
        if (fixedTime != null) {
            builder.fixedTime(fixedTime);
        }
        return builder.build();
    }
    private static EntityType entityTypeFromJson(JsonReader reader) throws IOException {
        return EntityType.fromNamespaceId(Objects.requireNonNull(namespaceFromJson(reader)));
    }

    private static Material materialFromJson(JsonReader reader) throws IOException {
        return Material.fromNamespaceId(Objects.requireNonNull(namespaceFromJson(reader)));
    }

    private static FloatRange floatRangeFromJson(JsonReader reader) throws IOException {
        return JsonUtils.typeMap(reader, token -> switch (token) {
            case BEGIN_ARRAY -> json -> {
                    json.beginArray();
                    var range = new FloatRange((float) json.nextDouble(), (float) json.nextDouble());
                    json.endArray();
                    return range;
                };
            case NUMBER -> json -> new FloatRange((float) json.nextDouble());
            default -> null;
        });
    }

    private static DoubleList doubleListFromJson(JsonReader reader) throws IOException {
        return JsonUtils.typeMap(reader, token -> switch (token) {
            case BEGIN_ARRAY -> json -> {
                    json.beginArray();
                    DoubleList list = new DoubleArrayList();
                    while (json.peek() == JsonReader.Token.NUMBER) {
                        list.add(json.nextDouble());
                    }
                    json.endArray();
                    return DoubleLists.unmodifiable(list);
                };
            default -> null;
        });
    }

    private static final Pattern GENERICS = Pattern.compile("<.*>");

    private static boolean typeDoesntMatch(Type type, Class<?> clazz) {
        String typeName = GENERICS.matcher(type.getTypeName()).replaceAll("");
        String clazzName = GENERICS.matcher(clazz.getTypeName()).replaceAll("");
        return !typeName.equals(clazzName);
    }

}
