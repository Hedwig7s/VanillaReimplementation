package net.minestom.vanilla.datapack.worldgen;

import net.kyori.adventure.nbt.*;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.vanilla.files.ByteArray;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DataVersion: Data version of the NBT structure.
 * author: Name of the player who created this structure. Only exists for structures saved before 1.13.
 * size: 3 TAG_Int describing the size of the structure.
 * palette: Set of different block states used in the structure.
 * A block.
 * Name: Block ID.
 * Properties: List of block state properties, with [name] being the name of the block state property.
 * Name: The block state name and its value.
 * palettes: Sets of different block states used in the structure, a random palette gets selected based on coordinates. Used in vanilla by shipwrecks.
 * A set of different block states used in the structure.
 * A block.
 * Name: Block ID.
 * Properties: List of block state properties, with [name] being the name of the block state property.
 * Name: The block state name and its value.
 * blocks: List of individual blocks in the structure.
 * An individual block.
 * state: Index of the block in the palette.
 * pos: 3 TAG_Int describing the position of this block.
 * nbt: NBT of the associated block entity (optional, only present if the block has one). Does not contain x, y, or z fields. See Block entity format.
 * entities: List of entities in the structure.
 * An entity.
 * pos: 3 TAG_Double describing the exact position of the entity.
 * blockPos: 3 TAG_Int describing the block position of the entity.
 * nbt: NBT of the entity (required). See entity format.
 *
 * @param DataVersion Data version of the NBT structure.
 * @param author      Name of the player who created this structure. Only exists for structures saved before 1.13.
 * @param size        3 TAG_Int describing the size of the structure.
 * @param palette     Set of different block states used in the structure.
 * @param palettes    Sets of different block states used in the structure, a random palette gets selected based on coordinates. Used in vanilla by shipwrecks.
 * @param blocks      List of individual blocks in the structure.
 * @param entities    List of entities in the structure.
 */
public record Structure(int DataVersion, @Nullable String author, Point size,
                        @Nullable Set<BlockState> palette, @UnknownNullability Set<Set<BlockState>> palettes,
                        List<Block> blocks, List<Entity> entities) {
    public static Structure fromInput(ByteArray content) {
        try {

            CompoundBinaryTag root = BinaryTagIO.reader().read(content.toStream());
            Objects.requireNonNull(root);

            int DataVersion = Objects.requireNonNull(root.getInt("DataVersion"));
            @Nullable String author = root.getString("author");

            ListBinaryTag nbt_size = Objects.requireNonNull(root.getList("size"));
            ListBinaryTag nbt_palette = root.getList("palette");
            ListBinaryTag nbt_palettes = root.getList("palettes");
            ListBinaryTag nbt_blocks = Objects.requireNonNull(root.getList("blocks"));
            ListBinaryTag nbt_entities = Objects.requireNonNull(root.getList("entities"));

            Point size = parsePoint(nbt_size);
            // Only one of "palette" OR "palettes" is present
            Set<BlockState> palette = nbt_palette == null ? null : parsePalette(nbt_palette);
            Set<Set<BlockState>> palettes = nbt_palettes == null ? null : parsePalettes(nbt_palettes);
            List<Block> blocks = parseBlocks(nbt_blocks);
            List<Entity> entities = parseEntities(nbt_entities);

            return new Structure(DataVersion, author, size, palette, palettes, blocks, entities);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Point parsePoint(/* NBTList<NBTInt> */ ListBinaryTag nbtSize) {
        return new Vec(nbtSize.getInt(0), nbtSize.getInt(1), nbtSize.getInt(2));
    }

    private static Point parseDoublePoint(/* NBTList<NBTDouble> */ ListBinaryTag nbtSize) {
        return new Vec(nbtSize.getDouble(0), nbtSize.getDouble(1), nbtSize.getDouble(2));
    }

    private static BlockState parseBlockState(CompoundBinaryTag block) {
        String blockId = Objects.requireNonNull(block.getString("Name"));
        CompoundBinaryTag properties = block.getCompound("Properties");
        if (properties.size() < 1) {
            return new BlockState(blockId, Map.of());
        }

        Map<String, String> propertyMap = new HashMap<>();
        for (Map.Entry<String, ? extends BinaryTag> property : properties) {
            propertyMap.put(property.getKey(), ((StringBinaryTag) property.getValue()).value());
        }

        return new BlockState(
          blockId,
          propertyMap
        );
    }

    private static Set<BlockState> parsePalette(/* NBTList<NBTCompound> */ ListBinaryTag nbtPalette) {
        return nbtPalette.stream()
          .map(tag -> parseBlockState((CompoundBinaryTag) tag))
          .collect(Collectors.toSet());
    }

    private static Set<Set<BlockState>> parsePalettes(/* NBTList<NBTList<NBTCompound>> */ ListBinaryTag nbtPalettes) {
        return nbtPalettes.stream()
          .map(palette -> (((ListBinaryTag) palette)).stream()
            .map(tag -> parseBlockState((CompoundBinaryTag) tag))
            .collect(Collectors.toSet()))
          .collect(Collectors.toSet());
    }

    private static List<Block> parseBlocks(/* NBTList<NBTCompound> */ ListBinaryTag nbtBlocks) {
        return nbtBlocks.stream()
          .map(tag -> {
              CompoundBinaryTag block = (CompoundBinaryTag) tag;
              return new Block(
                block.getInt("state"),
                parsePoint(Objects.requireNonNull(block.getList("pos"))),
                block.get("nbt")
              );
          })
          .collect(Collectors.toList());
    }

    private static List<Entity> parseEntities(/* NBTList<NBTCompound> */ ListBinaryTag nbtEntities) {
        return nbtEntities.stream()
          .map(tag -> {
              CompoundBinaryTag entity = (CompoundBinaryTag) tag;
              return new Entity(
                parseDoublePoint(Objects.requireNonNull(entity.getList("pos"))),
                parsePoint(Objects.requireNonNull(entity.getList("blockPos"))),
                Objects.requireNonNull(entity.getCompound("nbt"))
              );
          })
          .collect(Collectors.toList());
    }

    public record Block(int state, Point pos, @Nullable BinaryTag nbt) {
    }

    public record Entity(Point pos, Point blockPos, BinaryTag nbt) {
    }
}
