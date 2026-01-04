using System.Diagnostics;

namespace Misc;

internal class Util
{
    // A .NET tick is 100 nanoseconds.
    private const long NanoSecondsPerTick = 100;
    private static readonly Stopwatch _stopwatch = Stopwatch.StartNew();

    internal static long CurrentTimeMillis()
    {
        return DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    }

    internal static long NanoTime()
    {
        long ticks = _stopwatch.ElapsedTicks;
        return (long)((double)ticks / Stopwatch.Frequency * 1_000_000_000);
    }

    internal static TimeSpan MillisToTimeSpan(long milliseconds)
    {
        return TimeSpan.FromMilliseconds(milliseconds);
    }

    internal static TimeSpan NanosToTimeSpan(long nanoseconds)
    {
        // Calculate the number of .NET Ticks
        long ticks = nanoseconds / NanoSecondsPerTick;
        return new TimeSpan(ticks);
    }

    /// <summary>
    /// Converts a byte array into a Java-style String representation: [b1, b2, b3, ...]
    /// </summary>
    /// <param name="array">The byte array to convert.</param>
    /// <returns>A string representation.</returns>
    public static string ByteArrayToString(byte[] array)
    {
        return ByteArrayToString(array, 0, array.Length);
    }

    /// <summary>
    /// Converts a segment of a byte array (startIndex to length) 
    /// into a Java-style String representation: [b1, b2, b3, ...].
    /// </summary>
    /// <param name="array">The byte array to convert.</param>
    /// <param name="startIndex">The index to start converting from.</param>
    /// <param name="length">The number of bytes to include in the string.</param>
    /// <returns>A string representation of the specified segment.</returns>
    public static string ByteArrayToString(byte[] array, int startIndex, int length)
    {
        if (array == null)
        {
            return "null";
        }

        // Use LINQ to select the specific segment
        var segment = array.Skip(startIndex).Take(length);

        // Check if the segment is empty to handle edge cases
        if (!segment.Any())
        {
            return "[]";
        }

        // Use string.Join to format the elements with commas and spaces
        string content = string.Join(", ", segment.Select(b => b.ToString()));

        // Wrap the result in brackets
        return $"[{content}]";
    }
}
