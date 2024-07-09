package net.minestom.vanilla.datapack.loot.function;

import com.squareup.moshi.JsonReader;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagType;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.color.DyeColor;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.component.HeadProfile;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.tag.Tag;
import net.minestom.server.utils.NamespaceID;
import net.minestom.vanilla.datapack.DatapackLoader;
import net.minestom.vanilla.datapack.json.JsonUtils;
import net.minestom.vanilla.datapack.loot.LootTable;
import net.minestom.vanilla.datapack.loot.NBTPath;
import net.minestom.vanilla.datapack.loot.context.LootContext;
import net.minestom.vanilla.datapack.number.NumberProvider;
import net.minestom.vanilla.tag.Tags;
import net.minestom.vanilla.utils.JavaUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.random.RandomGenerator;

@SuppressWarnings("unused")
interface InBuiltLootFunctions {

    // Applies a predefined bonus formula to the count of the item stack.
    interface ApplyBonus extends LootFunction {

        @Override
        default NamespaceID function() {
            return NamespaceID.from("minecraft:apply_bonus");
        }

        DynamicRegistry.Key<Enchantment> enchantment();

        NamespaceID formula();

        static ApplyBonus fromJson(JsonReader reader) throws IOException {
            return JsonUtils.unionStringTypeMapAdapted(reader, "formula", Map.of(
              "minecraft:binomial_with_bonus_count", BinomialWithBonusCount.class,
              "minecraft:ore_drops", OreDrops.class,
              "minecraft:uniform_bonus_count", UniformBonusCount.class
            ));
        }

        record BinomialWithBonusCount(DynamicRegistry.Key<Enchantment> enchantment, Parameters parameters) implements ApplyBonus {
            @Override
            public NamespaceID formula() {
                return NamespaceID.from("minecraft:binomial_with_bonus_count");
            }

            public record Parameters(int extra, double probability) {
            }

            @Override
            public ItemStack apply(Context context) {
                int enchantLevel = context.itemStack().get(ItemComponent.ENCHANTMENTS, EnchantmentList.EMPTY).level(enchantment);
                double n = enchantLevel + parameters().extra();

                RandomGenerator random = context.random();
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    if (random.nextDouble() < parameters().probability()) {
                        sum++;
                    }
                }
                return context.itemStack().withAmount((int) sum);
            }
        }

        record UniformBonusCount(DynamicRegistry.Key<Enchantment> enchantment, Parameters parameters) implements ApplyBonus {

            @Override
            public NamespaceID formula() {
                return NamespaceID.from("minecraft:uniform_bonus_count");
            }

            public record Parameters(double bonusMultiplier) {
            }

            @Override
            public ItemStack apply(Context context) {
                int enchantLevel = context.itemStack().get(ItemComponent.ENCHANTMENTS, EnchantmentList.EMPTY).level(enchantment);
                double n = enchantLevel * parameters.bonusMultiplier();
                int count = (int) context.random().nextDouble(n);
                return context.itemStack().withAmount(count);
            }
        }

        record OreDrops(DynamicRegistry.Key<Enchantment> enchantment) implements ApplyBonus {

            @Override
            public NamespaceID formula() {
                return NamespaceID.from("minecraft:ore_drops");
            }

            @Override
            public ItemStack apply(Context context) {
                int enchantLevel = context.itemStack().get(ItemComponent.ENCHANTMENTS, EnchantmentList.EMPTY).level(enchantment);
                int itemCount = context.itemStack().amount() * (1 + context.random().nextInt(enchantLevel + 2));
                return context.itemStack().withAmount(itemCount);
            }
        }
    }

    // Copies an entity's or a block entity's name tag into the item's display.Name tag.
    record CopyName(LootContext.Trait<?> source) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:copy_name");
        }

        @Override
        public ItemStack apply(Context context) {
            Object entityOrBlockEntity = context.getOrThrow(source);
            @Nullable Component name = null;
            if (entityOrBlockEntity instanceof Entity entity) {
                name = entity.getCustomName();
            }
            if (entityOrBlockEntity instanceof Block blockEntity) {
                BlockHandler handler = blockEntity.handler();
                if (handler == null) return context.itemStack();
                // TODO: This is not the correct way to get the block entity's name
                name = Component.text(handler.getNamespaceId().value());
            }
            if (name == null) return context.itemStack();
            return context.itemStack().with(ItemComponent.CUSTOM_NAME, name);
        }
    }

    // Copies BinaryTag values from a specified block entity or entity, or from command storage to the item's tag tag.
    record CopyNbt(Source source, List<Operation> operations) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:copy_nbt");
        }

        @Override
        public ItemStack apply(Context context) {
            BinaryTag sourceNbt = source.nbt(context);
            BinaryTag itemStackTagNbt = context.itemStack().getTag(Tags.Items.TAG);
            for (Operation operation : operations) {
                itemStackTagNbt = operation.applyOperation(sourceNbt, itemStackTagNbt);
            }
            return context.itemStack().withTag(Tags.Items.TAG, itemStackTagNbt);
        }

        public sealed interface Source {

            String type();

            BinaryTag nbt(LootFunction.Context context);

            static Source fromJson(JsonReader reader) throws IOException {
                return JsonUtils.<Source>typeMap(reader, token -> switch (token) {
                    case STRING -> //noinspection unchecked
                      json -> new Context(DatapackLoader.moshi(LootContext.Trait.class).apply(json));
                    case BEGIN_OBJECT -> json -> JsonUtils.unionStringTypeAdapted(json, "type", type -> switch (type) {
                        case "storage" -> Storage.class;
                        case "context" -> Context.class;
                        default -> null;
                    });
                    default -> null;
                });
            }

            record Context(LootContext.Trait<CompoundBinaryTag> target) implements Source {

                @Override
                public String type() {
                    return "context";
                }

                @Override
                public CompoundBinaryTag nbt(LootFunction.Context context) {
                    return context.getOrThrow(target);
                }
            }

            record Storage(NamespaceID storageID) implements Source {

                @Override
                public String type() {
                    return "storage";
                }

                @Override
                public CompoundBinaryTag nbt(LootFunction.Context context) {
                    // TODO: Fetch command storage?
                    return CompoundBinaryTag.empty();
                }
            }
        }

        public sealed interface Operation {

            NBTPath.Single source();

            NBTPath.Single target();

            String op();

            BinaryTag apply(BinaryTag source, BinaryTag target);

            default BinaryTag applyOperation(BinaryTag source, BinaryTag itemStackNbt) {
                BinaryTag sourceNbt = source().getSingle(source);
                BinaryTag targetNbt = target().getSingle(itemStackNbt);
                BinaryTag newNbt = apply(sourceNbt, targetNbt);
                return target().set(itemStackNbt, newNbt);
            }

            static Operation fromJson(JsonReader reader) throws IOException {
                return JsonUtils.unionStringTypeAdapted(reader, "op", key -> switch (key) {
                    case "replace" -> Replace.class;
                    case "merge" -> Merge.class;
                    case "append" -> Append.class;
                    default -> null;
                });
            }

            record Replace(NBTPath.Single source, NBTPath.Single target) implements Operation {
                @Override
                public String op() {
                    return "replace";
                }

                @Override
                public BinaryTag apply(BinaryTag source, BinaryTag target) {
                    return source;
                }
            }

            record Merge(NBTPath.Single source, NBTPath.Single target) implements Operation {
                @Override
                public String op() {
                    return "merge";
                }

                @Override
                public BinaryTag apply(BinaryTag source, BinaryTag target) {
                    if (!(source instanceof CompoundBinaryTag sourceCompound && target instanceof CompoundBinaryTag targetCompound)) {
                        throw new IllegalArgumentException("Cannot merge non-compound BinaryTag types");
                    }
                    //noinspection unchecked
                    return targetCompound.put(sourceCompound);
                }
            }

            record Append(NBTPath.Single source, NBTPath.Single target) implements Operation {
                @Override
                public String op() {
                    return "append";
                }

                @Override
                public BinaryTag apply(BinaryTag source, BinaryTag target) {
                    if (!(source instanceof ListBinaryTag sourceList && target instanceof ListBinaryTag targetList)) {
                        throw new IllegalArgumentException("Cannot append non-list BinaryTag types");
                    }

                    BinaryTagType<?> sourceType = sourceList.elementType();
                    BinaryTagType<?> targetType = targetList.elementType();

                    if (sourceType != targetType) {
                        throw new IllegalArgumentException("Cannot append lists of different types");
                    }

                    for (BinaryTag nbt : sourceList) {
                        targetList.add(nbt);
                    }
                    return targetList;
                }
            }
        }
    }

    // Copies block state properties provided by loot context to the item's BlockStateTag tag.
    record CopyState(Block block, List<String> properties) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:copy_state");
        }

        @Override
        public ItemStack apply(Context context) {
            Block blockState = context.getOrThrow(LootContext.BLOCK_STATE);
            Map<String, String> blockProperties = blockState.properties();

            CompoundBinaryTag nbt = context.itemStack().getTag(Tags.Items.BLOCKSTATE);
            for (String property : properties) {
                String value = blockProperties.get(property);
                if (value == null) {
                    throw new IllegalArgumentException("Block " + blockState + " does not have property " + property);
                }
                nbt.putString(property, value);
            }
            return context.itemStack().withTag(Tags.Items.BLOCKSTATE, nbt);
        }
    }

    // Enchants the item with one randomly-selected enchantment. The power of the enchantment, if applicable, is random.
    // A book will convert to an enchanted book when enchanted.
    record EnchantRandomly(List<Enchantment> enchantments) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:enchant_randomly");
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            if (Material.BOOK.equals(itemStack.material())) {
                itemStack = itemStack.withMaterial(Material.ENCHANTED_BOOK);
            }

            // TODO: Find discoverable enchantments
            // For now we will just use all possible enchantments, with level 1-3
            EnchantmentList enchantmentList = itemStack.get(ItemComponent.ENCHANTMENTS, new EnchantmentList(Collections.emptyMap()));
            for (Field field : Enchantment.class.getFields()) {
                try {
                    DynamicRegistry.Key<Enchantment> enchantment = (DynamicRegistry.Key<Enchantment>) field.get(null);
                    enchantmentList.with(enchantment, (short) context.random().nextInt(1, 4));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            return itemStack.with(ItemComponent.ENCHANTMENTS, enchantmentList);
        }
    }

    // Enchants the item, with the specified enchantment level(roughly equivalent to using an enchantment table at that
    // level). A book will convert to an enchanted book.
    record EnchantWithLevels(boolean treasure, NumberProvider levels) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:enchant_with_levels");
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            if (Material.BOOK.equals(itemStack.material())) {
                itemStack = itemStack.withMaterial(Material.ENCHANTED_BOOK);
            }

            NumberProvider.Context numberContext = context::random;

            // TODO: Proper enchanting system
            int level = levels.asInt().apply(numberContext) / 10;
            Field[] enchantmentFields = Enchantment.class.getFields();
            List<DynamicRegistry.Key<Enchantment>> enchantments = new ArrayList<>();
            for (Field field : enchantmentFields) {
                try {
                    DynamicRegistry.Key<Enchantment> enchantmentKey = (DynamicRegistry.Key<Enchantment>) field.get(null);
                    enchantments.add(enchantmentKey);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            DynamicRegistry.Key<Enchantment> randomEnchant = JavaUtils.randomElement(context.random(), enchantments);

            return itemStack.with(ItemComponent.ENCHANTMENTS, new EnchantmentList(new HashMap<>()).with(randomEnchant, level));
        }
    }

    // If the origin is provided by loot context, converts an empty map into an explorer map leading to a nearby
    // generated structure.
    record ExplorationMap(@Nullable String destination, @Nullable String decoration, @Nullable Integer zoom,
                          @Nullable Integer search_radius,
                          @Nullable Boolean skip_existing_chunks) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:exploration_map");
        }

        @Override
        public ItemStack apply(Context context) {
            String destination = Objects.requireNonNullElse(this.destination, "on_treasure_maps");
            String decoration = Objects.requireNonNullElse(this.decoration, "mansion");
            int zoom = Objects.requireNonNullElse(this.zoom, 2);
            int searchRadius = Objects.requireNonNullElse(this.search_radius, 50);
            boolean skipExistingChunks = Objects.requireNonNullElse(this.skip_existing_chunks, true);

            ItemStack itemStack = context.itemStack();
            if (!Material.MAP.equals(itemStack.material())) {
                throw new IllegalArgumentException("Item stack must be a map");
            }
            itemStack = itemStack.withMaterial(Material.FILLED_MAP);

            // TODO: Exploration maps
            // converts an empty map into an explorer map leading to a nearby generated structure.

            return itemStack;
        }
    }


    // Removes some items from a stack, if the explosion ratius is provided by loot context.
    // Each item in the item stack has a chance of 1/explosion radius to be lost.
    record ExplosionDecay() implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:explosion_decay");
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            if (Material.AIR.equals(itemStack.material())) {
                return itemStack;
            }

            double explosionRadius = context.getOrThrow(LootContext.EXPLOSION_RADIUS);

            int itemStackCount = itemStack.amount();
            int removeAmount = 0;

            for (int i = 0; i < itemStackCount; i++) {
                if (context.random().nextDouble() < 1 / explosionRadius) {
                    removeAmount++;
                }
            }

            return itemStack.withAmount(itemStackCount - removeAmount);
        }
    }

    // Adds required item tags of a player head.
    record FillPlayerHead(LootContext.Trait<Entity> entity) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:fill_player_head");
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            if (!Material.PLAYER_HEAD.equals(itemStack.material())) {
                throw new IllegalArgumentException("Item stack must be a player head");
            }

            Entity entity = context.getOrThrow(this.entity);
            if (!(entity instanceof Player player)) {
                throw new IllegalArgumentException("Entity must be a player");
            }

            PlayerSkin playerSkin = PlayerSkin.fromUuid(String.valueOf(player.getUuid()));
            if(playerSkin == null) {
                return itemStack;
            }

            return itemStack.with(ItemComponent.PROFILE, new HeadProfile(playerSkin));
        }
    }

    // Smelts the item as it would be in a furnace without changing its count.
    record FurnaceSmelt() implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:furnace_smelt");
        }

        @Override
        public ItemStack apply(Context context) {
            // TODO: Furnace smelting
            return context.itemStack();
        }
    }

    // Limits the count of every item stack.
    record LimitCount(Limit limit) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:limit_count");
        }

        @Override
        public ItemStack apply(Context context) {
            return limit().limit(context);
        }

        public interface Limit {
            ItemStack limit(LootFunction.Context context);

            static Limit fromJson(JsonReader reader) throws IOException {
                return JsonUtils.typeMapMapped(reader, Map.of(
                  JsonReader.Token.NUMBER, DatapackLoader.moshi(Constant.class),
                  JsonReader.Token.BEGIN_OBJECT, DatapackLoader.moshi(MinMax.class)
                ));
            }

            record Constant(int limit) implements Limit {
                @Override
                public ItemStack limit(LootFunction.Context context) {
                    return context.itemStack().withAmount(amount -> Math.min(amount, limit));
                }
            }

            record MinMax(@Nullable NumberProvider min, @Nullable NumberProvider max) implements Limit {
                @Override
                public ItemStack limit(LootFunction.Context context) {
                    return context.itemStack().withAmount(amount -> {
                        if (min != null) amount = Math.max(amount, min.asInt().apply(context::random));
                        if (max != null) amount = Math.min(amount, max.asInt().apply(context::random));
                        return amount;
                    });
                }
            }
        }
    }

    // Adjusts the stack size based on the level of the Looting enchantment on the killer entity provided by loot context.
    record LootingEnchant(NumberProvider count, @Nullable Integer limit) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:looting_enchant");
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            if (Material.AIR.equals(itemStack.material())) {
                return itemStack;
            }

            Entity killer = context.getOrThrow(LootContext.KILLER_ENTITY);

            int looting;
            if (killer instanceof Player player) {
                ItemStack mainHand = player.getItemInMainHand();
                looting = (short) mainHand.get(ItemComponent.ENCHANTMENTS, EnchantmentList.EMPTY).level(Enchantment.LOOTING);
            } else {
                // TODO: Other entities that hold an item
                return itemStack;
            }

            double additionalMultipler = count.asDouble().apply(context::random);
            int additional = (int) Math.floor(looting * additionalMultipler);
            return context.itemStack().withAmount(amount -> amount + additional);
        }
    }

    // Add attribute modifiers to the item.
    record SetAttributes(List<AttributeModifier> modifiers) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_attributes");
        }

//        @Override
//        public ItemStack apply(Context context) {
//            ItemStack itemStack = context.itemStack();
//
//            AttributeList attributeList = itemStack.get(ItemComponent.ATTRIBUTE_MODIFIERS, new AttributeList(Collections.emptyList()));
//            for (AttributeList.Modifier modifier : modifiers) {
//                attributeList = attributeList.with(modifier);
//            }
//
//            return itemStack.with(ItemComponent.ATTRIBUTE_MODIFIERS, attributeList);
//        }


        @Override
        public ItemStack apply(Context context) {
            List<AttributeList.Modifier> newAttributes = modifiers().stream()
              .map(modifier -> modifier.apply(context))
              .toList();

            AttributeList oldAttributes = context.itemStack().get(ItemComponent.ATTRIBUTE_MODIFIERS, new AttributeList(Collections.emptyList()));

            ArrayList<AttributeList.Modifier> combinedAttributes = new ArrayList<>();
            combinedAttributes.addAll(oldAttributes.modifiers());
            combinedAttributes.addAll(newAttributes);

            return context.itemStack().with(ItemComponent.ATTRIBUTE_MODIFIERS, new AttributeList(combinedAttributes));
        }

        public enum Operation {
            ADDITION("addition"),
            MULTIPLY_BASE("multiply_base"),
            MULTIPLY_TOTAL("multiply_total");

            private final String id;

            Operation(String id) {
                this.id = id;
            }

            public String getId() {
                return id;
            }

            public AttributeOperation toMinestom() {
                return switch (this) {
                    case ADDITION -> AttributeOperation.ADD_VALUE;
                    case MULTIPLY_BASE -> AttributeOperation.MULTIPLY_BASE;
                    case MULTIPLY_TOTAL -> AttributeOperation.MULTIPLY_TOTAL;
                };
            }
        }

        public enum Slot {
            MAINHAND("mainhand"),
            OFFHAND("offhand"),
            FEET("feet"),
            LEGS("legs"),
            CHEST("chest"),
            HEAD("head");

            private final String id;

            Slot(String id) {
                this.id = id;
            }

            public String getId() {
                return id;
            }

            public EquipmentSlotGroup toMinestom() {
                return switch (this) {
                    case MAINHAND -> EquipmentSlotGroup.MAIN_HAND;
                    case OFFHAND -> EquipmentSlotGroup.OFF_HAND;
                    case FEET -> EquipmentSlotGroup.FEET;
                    case LEGS -> EquipmentSlotGroup.LEGS;
                    case CHEST -> EquipmentSlotGroup.CHEST;
                    case HEAD -> EquipmentSlotGroup.HEAD;
                };
            }
        }

        public record AttributeModifier(String name, NamespaceID attribute, Operation operation, NumberProvider amount,
                                        @Nullable UUID id, List<Slot> slot) {

            public AttributeModifier(String name, NamespaceID attribute, Operation operation, NumberProvider amount,
                                     @Nullable UUID id, Slot slot) {
                this(name, attribute, operation, amount, id, List.of(slot));
            }

            public AttributeList.Modifier apply(Context context) {
                UUID uuid = Objects.requireNonNullElseGet(id(), UUID::randomUUID);
                Attribute attribute = Attribute.fromNamespaceId(attribute().path());
                AttributeOperation operation = operation().toMinestom();
                double amount = amount().asDouble().apply(context::random);
                EquipmentSlotGroup slot = JavaUtils.randomElement(context.random(), slot()).toMinestom();

                if (attribute == null) {
                    throw new IllegalArgumentException("Invalid attribute: " + attribute());
                }

                return new AttributeList.Modifier(
                  attribute,
                  new net.minestom.server.entity.attribute.AttributeModifier(
                    name, amount, operation
                  ),
                  slot
                );
            }
        }
    }

    // Adds or replaces banner patterns of a banner. Function successfully adds patterns into BinaryTag tag even if invoked on a non-banner.
    record SetBannerPattern(List<Pattern> patterns, boolean append) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_banner_pattern");
        }

        public record Pattern(String pattern, String color) {
            public Tags.Items.Banner.Pattern toPattern() {
                int color = DyeColor.valueOf(this.color().toUpperCase(Locale.ROOT)).ordinal();
                return new Tags.Items.Banner.Pattern(pattern(), color);
            }
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            if (Material.AIR.equals(itemStack.material())) {
                return itemStack;
            }

            List<Tags.Items.Banner.Pattern> patternsList;
            if (append) {
                patternsList = new ArrayList<>(itemStack.getTag(Tags.Items.Banner.PATTERNS));
            } else {
                patternsList = new ArrayList<>();
            }

            for (Pattern pattern : patterns()) {
                patternsList.add(pattern.toPattern());
            }

            return itemStack.withTag(Tags.Items.Banner.PATTERNS, patternsList);
        }
    }

    // Sets the contents of a container block item to a list of entries.
    record SetContents(List<LootTable.Pool.Entry> entries, EntityType type) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_contents");
        }

        @Override
        public ItemStack apply(Context context) {
            // TODO: Implement
            return context.itemStack();
        }
    }

    // Sets the stack size.
    record SetCount(NumberProvider count, @Nullable Boolean add) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_count");
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            if (Material.AIR.equals(itemStack.material())) {
                return itemStack;
            }

            boolean add = Objects.requireNonNullElse(this.add, false);

            int amount = count.asInt().apply(context::random);
            if (add) {
                amount += itemStack.amount();
            } else {
                amount = Math.min(amount, itemStack.material().maxStackSize());
            }
            return itemStack.withAmount(amount);
        }
    }

    // Sets the item's damage value (durability).
    record SetDamage(NumberProvider damage, @Nullable Boolean add) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_damage");
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            if (Material.AIR.equals(itemStack.material())) {
                return itemStack;
            }

            boolean add = Objects.requireNonNullElse(this.add, false);

            int damage;
            if (add) {
                damage = itemStack.get(ItemComponent.DAMAGE, 0) + this.damage.asInt().apply(context::random);
            } else {
                damage = this.damage.asInt().apply(context::random);
            }
            return itemStack.with(ItemComponent.DAMAGE, damage);
        }
    }

    // Modifies the item's enchantments. A book will convert to an enchanted book.
    record SetEnchantments(Map<DynamicRegistry.Key<Enchantment>, NumberProvider> enchantments,
                           @Nullable Boolean add) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_enchantments");
        }

        @Override
        public ItemStack apply(Context context) {
            boolean add = Objects.requireNonNullElse(this.add, false);
            ItemStack itemStack = context.itemStack();

            Map<DynamicRegistry.Key<Enchantment>, Integer> enchantmentMap = itemStack.get(ItemComponent.ENCHANTMENTS, EnchantmentList.EMPTY).enchantments();

            for (var entry : enchantments.entrySet()) {
                DynamicRegistry.Key<Enchantment> enchantment = entry.getKey();
                int count = entry.getValue().asInt().apply(context::random);

                if (add) {
                    int previousValue = enchantmentMap.getOrDefault(enchantment, 0);
                    int newValue = previousValue + count;
                    enchantmentMap.put(enchantment, newValue);
                } else {
                    enchantmentMap.put(enchantment, count);
                }
            }

            return itemStack.with(ItemComponent.ENCHANTMENTS, new EnchantmentList(enchantmentMap));
        }
    }

    // Sets the item tags for instrument items to a random value from a tag.
    record SetInstrument(NamespaceID options) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_instrument");
        }

        @Override
        public ItemStack apply(Context context) {
            // TODO: Implement
            return context.itemStack();
        }
    }

    // Sets the loot table for a container block when placed and opened.
    record SetLootTable(NamespaceID name, @Nullable Integer seed, String type) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_loot_table");
        }

        @Override
        public ItemStack apply(Context context) {
            // TODO: Implement
            return context.itemStack();
        }
    }

    // Adds or changes the item's lore.
    record SetLore(List<Component> lore, String entity, @Nullable Boolean replace) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_lore");
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            if (Material.AIR.equals(itemStack.material())) {
                return itemStack;
            }

            boolean replace = Objects.requireNonNullElse(this.replace, false);

            List<Component> newLore;
            if (replace) {
                newLore = lore();
            } else {
                newLore = itemStack.get(ItemComponent.LORE, new ArrayList<>());
                newLore.addAll(lore());
            }
            return itemStack.with(ItemComponent.LORE, newLore);
        }
    }

    // Adds or changes the item's custom name.
    record SetName(Component name, String entity) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_name");
        }

        @Override
        public ItemStack apply(Context context) {
            return context.itemStack().with(ItemComponent.CUSTOM_NAME, name);
        }
    }

    // Adds or changes BinaryTag data of the item.
    record SetNBT(CompoundBinaryTag nbt) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_nbt");
        }

        @Override
        public ItemStack apply(Context context) {
            ItemStack itemStack = context.itemStack();
            for (Map.Entry<String, ? extends BinaryTag> entry : nbt) {
                itemStack = itemStack.withTag(Tag.NBT(entry.getKey()), entry.getValue());
            }
            return itemStack;
        }
    }

    // Sets the Potion tag of an item.
    record SetPotion(NamespaceID potion) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_potion");
        }

        @Override
        public ItemStack apply(Context context) {
            return context.itemStack().withTag(Tags.Items.Potion.POTION, potion);
        }
    }

    // Sets the status effects for suspicious stew. Fails if invoked on an item that is not suspicious stew.
    record SetStewEffect(List<Effect> effects) implements LootFunction {

        @Override
        public NamespaceID function() {
            return NamespaceID.from("minecraft:set_stew_effect");
        }

        public record Effect(String type, NumberProvider duration) {
        }

        @Override
        public ItemStack apply(Context context) {
            if (!Material.SUSPICIOUS_STEW.equals(context.itemStack().material())) {
                throw new IllegalArgumentException("Cannot set stew effect on non-stew item");
            }
            // TODO: Implement
            return context.itemStack();
        }
    }
}
