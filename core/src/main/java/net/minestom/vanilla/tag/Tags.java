package net.minestom.vanilla.tag;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import net.minestom.server.utils.NamespaceID;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public interface Tags {


    interface Items {
        Tag<BinaryTag> TAG = Tag.NBT("tag")
          .defaultValue(CompoundBinaryTag.empty());
        Tag<CompoundBinaryTag> BLOCKSTATE = TAG.path("BlockEntityTag")
          .map(nbt -> nbt instanceof CompoundBinaryTag ? (CompoundBinaryTag) nbt : CompoundBinaryTag.empty(), nbt -> nbt)
          .defaultValue(CompoundBinaryTag.empty());

        interface Banner {
            record Pattern(String pattern, int color) {
            }

            Tag<List<Pattern>> PATTERNS = BLOCKSTATE.path("Patterns") // TODO: Is this correct?
              .map(nbt -> new Pattern(
                nbt.getString("Pattern"),
                nbt.getInt("Color")
              ), pattern -> CompoundBinaryTag.builder()
                .putString("Pattern", pattern.pattern())
                .putInt("Color", pattern.color())
                .build()).list();
        }

        interface Potion {
            Tag<NamespaceID> POTION = TAG.path("Potion")
              .map(nbt -> nbt instanceof StringBinaryTag nbts ?
                  NamespaceID.from(nbts.value()) : NamespaceID.from("minecraft:empty"),
                nbt -> StringBinaryTag.stringBinaryTag(nbt.toString()));
        }
    }

    interface Blocks {
        interface Campfire {
            Tag<List<ItemStack>> ITEMS = Tag.NBT("Items")
              .map(nbt -> nbt instanceof CompoundBinaryTag ? (CompoundBinaryTag) nbt : CompoundBinaryTag.empty(), nbt -> nbt)
              .list()
              .map(nbtList -> nbtList.stream()
                  .map(nbt -> ItemStack.of(Material.fromNamespaceId(nbt.getString("id"))))
                  .collect(Collectors.toList()),
                items -> IntStream.range(0, items.size())
                  .mapToObj(slot -> CompoundBinaryTag.builder()
                    .putString("id", items.get(slot).material().name())
                    .putByte("Slot", (byte) slot)
                    .putByte("Count", (byte) 1)
                    .build()
                  ).toList());

            // The number of ticks that the campfire has been cooking for, for each of the 4 slots, 0 means end of the cooking
            Tag<List<Integer>> COOKING_PROGRESS = Tag.Integer("vri:campfire:cooking_progress").list().defaultValue(List.of(0, 0, 0, 0));

        }

        interface Smelting {
            // The number of ticks that the furnace can cook for without using another fuel item
            Tag<Integer> COOKING_TICKS = Tag.Integer("vri:furnace:cooking_ticks").defaultValue(0);

            // The last fuel item that was used. Null if this furnace is not currently burning
            Tag<Material> LAST_COOKED_ITEM = Tag.String("vri:furnace:last_cooked_item")
              .map(Material::fromNamespaceId, mat -> mat.namespace().toString())
              .defaultValue(Material.AIR);

            // The number of ticks that the furnace has been cooking for
            Tag<Integer> COOKING_PROGRESS = Tag.Integer("vri:furnace:cooking_progress").defaultValue(0);
        }
    }
}
