using System.Buffers;
using System.IO.Pipelines;

namespace TelegramServer
{
    public static class PipelinesFrameDecoder
    {
        public static async ValueTask<byte[]?> NextFrameAsync(PipeReader reader, byte[] delimiter, CancellationToken ct = default)
        {
            if (delimiter == null || delimiter.Length == 0) throw new ArgumentException("Delimiter empty");

            while (true)
            {
                // Get data from the PipeReader
                ReadResult result = await reader.ReadAsync(ct);
                ReadOnlySequence<byte> buffer = result.Buffer;

                // Search for the delimiter
                if (TryParseFrame(ref buffer, delimiter, out byte[]? frame))
                {
                    // Mark the data already read as consumed
                    reader.AdvanceTo(buffer.Start, buffer.End);
                    return frame;
                }

                // Read more data
                reader.AdvanceTo(buffer.Start, buffer.End);

                if (result.IsCompleted)
                {
                    return null; // Stream end reached
                }
            }
        }

        private static bool TryParseFrame(ref ReadOnlySequence<byte> buffer, byte[] delimiter, out byte[]? frame)
        {
            frame = null;
            var reader = new SequenceReader<byte>(buffer);

            // Special handling of ALIVE_REQUEST (0xFF)
            if (reader.TryPeek(out byte first) && first == Protocol.ALIVE_REQUEST)
            {
                reader.Advance(1);
                buffer = buffer.Slice(reader.Position);
                frame = Protocol.ALIVE_BYTE;
                return true;
            }

            // Search for the delimiter
            if (reader.TryReadTo(out ReadOnlySequence<byte> frameData, delimiter))
            {
                frame = frameData.ToArray(); // TODO: use MemoryPool!
                buffer = buffer.Slice(reader.Position);
                return true;
            }

            return false;
        }
    }
}
