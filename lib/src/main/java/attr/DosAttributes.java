package au.djac.jwalker.attr;

import java.nio.file.attribute.DosFileAttributes;

/**
 * Represents a set of DOS/Windows file attribute flags: read-only, hidden, system and archive.
 */
public final class DosAttributes
{
    /**
     * Create a {@code DosAttributes} instance based on the field used to store DOS (or Windows)
     * attributes.
     *
     * @param attr A DOS/Windows attribute field.
     * @return A new {@code DosAttributes} instance, reflecting the same attributes.
     */
    public static DosAttributes forAttrField(int attrField)
    {
        return new DosAttributes(attrField);
    }

    /**
     * Create a {@code DosAttributes} instance based on an existing {@link DosFileAttributes}
     * object, for compatibility with the JDK.
     *
     * @param attr A {@link DosFileAttributes} instance.
     * @return A new {@code DosAttributes} instance, reflecting the same attributes.
     */
    public static DosAttributes forAttr(DosFileAttributes attr)
    {
        return new DosAttributes(
            (attr.isReadOnly() ? 0x01 : 0) +
            (attr.isHidden()   ? 0x02 : 0) +
            (attr.isSystem()   ? 0x04 : 0) +
            (attr.isArchive()  ? 0x20 : 0)
        );
    }

    private int attrField;

    private DosAttributes(int attrField)
    {
        this.attrField = attrField;
    }

    public int getAttrField()   { return attrField; }
    public boolean isReadOnly() { return (attrField & 0x01) == 0x01; }
    public boolean isHidden()   { return (attrField & 0x02) == 0x02; }
    public boolean isSystem()   { return (attrField & 0x04) == 0x04; }
    public boolean isArchive()  { return (attrField & 0x20) == 0x20; }

    public String toString()
    {
        return new String(new char[] {
            isArchive()  ? 'A' : '-',
            isSystem()   ? 'S' : '-',
            isHidden()   ? 'H' : '-',
            isReadOnly() ? 'R' : '-'
        });
    }
}
