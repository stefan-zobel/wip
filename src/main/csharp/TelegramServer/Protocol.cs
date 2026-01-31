namespace TelegramServer;

public static class Protocol
{
    /// <summary>
    /// The length of most protocol messages
    /// </summary>
    public const int SIZE = 4;

    /// <summary>
    /// The ALIVE_BYTE constant
    /// </summary>
    public static readonly byte[] ALIVE_BYTE = [0xFF];

    /// <summary>
    /// The ALIVE_BYTE as an integer
    /// </summary>
    public const int ALIVE_REQUEST = 0xFF;

    /// <summary>
    /// The END_OF_REQUEST constant
    /// </summary>
    public static readonly byte[] END_OF_REQUEST = [0xF5, 0xF7, 0xF9, 0xC1];

    /// <summary>
    /// The REPLY_OK (ACK) constant
    /// </summary>
    public static readonly byte[] REPLY_OK = [0xF6, 0xF8, 0xFA, 0xC0];

    /// <summary>
    /// The REPLY_FAILURE constant
    /// </summary>
    public static readonly byte[] REPLY_FAILURE = [0xC0, 0xFB, 0xFC, 0xFD];

    /// <summary>
    /// Returns true when the given message byte array can be transported through our messaging protocol
    /// </summary>
    public static bool IsTransportableMessage(byte[]? msg)
    {
        int len = msg?.Length ?? 0;
        if (len == 0) return false;

        const byte alive = ALIVE_REQUEST;
        const byte eof0 = 0xF5;
        const byte ok0 = 0xF6;
        const byte fail0 = 0xC0;

        ReadOnlySpan<byte> msgSpan = msg;
        int i;

        for (i = 0; i <= len - 4; ++i)
        {
            byte b = msgSpan[i];
            if (b == alive)
            {
                return false;
            }

            if (b == eof0 || b == ok0 || b == fail0)
            {
                // SequenceEqual is highly optimized in .NET 9 (often vectorized/SIMD)
                // We use Slice to check 4-byte sequences
                ReadOnlySpan<byte> current = msgSpan.Slice(i, 4);
                if (current.SequenceEqual(END_OF_REQUEST) ||
                    current.SequenceEqual(REPLY_OK) ||
                    current.SequenceEqual(REPLY_FAILURE))
                {
                    return false;
                }
            }
        }

        // Remaining bytes (less than 4) only need to check for the single-byte ALIVE_REQUEST
        for (; i < len; ++i)
        {
            if (msgSpan[i] == alive)
            {
                return false;
            }
        }

        return true;
    }

    /// <summary>
    /// Returns true when the given reply byte array is the OK reply
    /// </summary>
    public static bool IsOkReply(byte[]? reply)
    {
        if (reply != null && reply.Length == SIZE)
        {
            // Using explicit indices as in original for performance, 
            // though Span.SequenceEqual would be equally fast here.
            return reply[0] == 0xF6 && reply[1] == 0xF8 && reply[2] == 0xFA && reply[3] == 0xC0;
        }
        return false;
    }

    /// <summary>
    /// Returns true when the given request byte array is the ALIVE request
    /// </summary>
    public static bool IsAliveRequest(byte[]? request)
    {
        if (request != null && request.Length == 1)
        {
            return request[0] == ALIVE_REQUEST;
        }
        return false;
    }
}

