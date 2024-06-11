package net.minestom.vanilla.datapack.loot;

import com.squareup.moshi.JsonReader;
import net.kyori.adventure.nbt.BinaryTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

public interface NBTPath {

    static NBTPath fromJson(JsonReader reader) throws IOException {
        return NBTPathImpl.readPath(new StringReader(reader.nextString()));
    }

    /**
     * Indexes the given BinaryTag using this path and returns the results.
     *
     * @param nbt the BinaryTag to index
     * @return a map of the path to the resulting BinaryTag
     */
    @NotNull Map<NBTPath.Single, BinaryTag> get(BinaryTag nbt);

    /**
     * A single path returns either a single BinaryTag value or nothing.
     * This makes it possible to set a single value in a BinaryTag structure as well.
     */
    interface Single extends NBTPath {

        static Single fromJson(JsonReader reader) throws IOException {
            return NBTPathImpl.readSingle(new StringReader(reader.nextString()));
        }

        /**
         * Gets the single result of this path, or returns null if there is not exactly one result.
         * @param nbt the BinaryTag to index
         * @return the single result, or null if there is not exactly one result
         */
        @Nullable BinaryTag getSingle(BinaryTag nbt);

        @Override
        @Deprecated
        default @NotNull Map<Single, BinaryTag> get(BinaryTag nbt) {
            BinaryTag single = getSingle(nbt);
            return single == null ? Map.of() : Map.of(this, single);
        }

        /**
         * Sets the value of this path in the given BinaryTag to the given value.
         * @param nbt the BinaryTag to set the value in
         * @param value the value to set
         * @return the new BinaryTag, or null if the value could not be set i.e. the path did not exist
         */
        @Nullable BinaryTag set(BinaryTag nbt, BinaryTag value);
    }
}
