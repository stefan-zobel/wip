using System.Buffers;
using System.IO.Pipelines;

namespace TelegramServer;

internal class PipelinesFrameDecoder2
{
    public static async Task RunProcessingLoopAsync(PipeReader reader, byte[] delimiter, IMessageHandler2 handler, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            ReadResult result = await reader.ReadAsync(ct);
            ReadOnlySequence<byte> buffer = result.Buffer;

            // Look for Frames
            while (TryParseFrame(ref buffer, delimiter, out ReadOnlySequence<byte>? frame))
            {
                handler.OnNext(frame.Value);
            }

            // Mark the data already read as consumed
            reader.AdvanceTo(buffer.Start, buffer.End);

            if (result.IsCompleted) break;
        }
        await reader.CompleteAsync();
    }

    private static bool TryParseFrame(ref ReadOnlySequence<byte> buffer, byte[] delimiter, out ReadOnlySequence<byte>? frame)
    {
        frame = null;
        var reader = new SequenceReader<byte>(buffer);

        // ALIVE_REQUEST (0xFF) logic
        if (reader.TryPeek(out byte first) && first == Protocol.ALIVE_REQUEST)
        {
            frame = buffer.Slice(reader.Position, 1);
            reader.Advance(1);
            buffer = buffer.Slice(reader.Position);
            return true;
        }

        // Search for the delimiter without copying
        if (reader.TryReadTo(out ReadOnlySequence<byte> data, delimiter))
        {
            frame = data;
            buffer = buffer.Slice(reader.Position);
            return true;
        }

        return false;
    }
}
